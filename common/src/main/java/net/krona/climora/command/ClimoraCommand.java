package net.krona.climora.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.krona.climora.Climora;
import net.krona.climora.climate.AtmosphereModel;
import net.krona.climora.climate.CellPos;
import net.krona.climora.climate.ClimateCell;
import net.krona.climora.climate.ClimateGrid;
import net.krona.climora.climate.ClimateInterpolation;
import net.krona.climora.climate.ClimateManager;
import net.krona.climora.climate.ClimateSimulation;
import net.krona.climora.climate.SimulationConstants;
import net.krona.climora.climate.SimulationStats;
import net.krona.climora.climate.WeatherOverride;
import net.krona.climora.config.ClimoraConfig;
import net.krona.climora.weather.LocalWeather;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * {@code /climora} commands, permission level 2:
 * <ul>
 *     <li>{@code temperature [pos]} — air temperature and weather at a position</li>
 *     <li>{@code cell [pos]} — full state of the weather cell at a position</li>
 *     <li>{@code weather clear|cloudy|rain|storm [radius] [seconds]} — force weather around you; {@code weather auto} ends it</li>
 *     <li>{@code stats} — simulation performance</li>
 *     <li>{@code stress <radius>} — simulate extra cells around players for load tests, 0 to stop</li>
 *     <li>{@code reload} — reload {@code config/climora.json}</li>
 * </ul>
 */
public final class ClimoraCommand {
    private static final String ARG_POS = "pos";
    private static final String ARG_RADIUS = "radius";
    private static final String ARG_SECONDS = "seconds";
    private static final int DEFAULT_OVERRIDE_RADIUS = 3;
    private static final int DEFAULT_OVERRIDE_SECONDS = 600;
    /** Area of the vanilla /weather command when it is redirected: 32 cells, about 4 km around the player. */
    private static final int VANILLA_WEATHER_RADIUS = 32;
    /** Vanilla /weather without a duration lasts a random 10-20 minutes; use 15. */
    private static final int VANILLA_DEFAULT_TICKS = 18000;

    private ClimoraCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context,
                                Commands.CommandSelection selection) {
        dispatcher.register(Commands.literal(Climora.MOD_ID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("temperature")
                        .executes(ctx -> showTemperature(ctx.getSource(), sourcePos(ctx)))
                        .then(Commands.argument(ARG_POS, BlockPosArgument.blockPos())
                                .executes(ctx -> showTemperature(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, ARG_POS)))))
                .then(Commands.literal("cell")
                        .executes(ctx -> showCell(ctx.getSource(), sourcePos(ctx)))
                        .then(Commands.argument(ARG_POS, BlockPosArgument.blockPos())
                                .executes(ctx -> showCell(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, ARG_POS)))))
                .then(Commands.literal("weather")
                        .then(overrideCommand("clear", WeatherOverride.Mode.CLEAR))
                        .then(overrideCommand("cloudy", WeatherOverride.Mode.CLOUDY))
                        .then(overrideCommand("rain", WeatherOverride.Mode.RAIN))
                        .then(overrideCommand("storm", WeatherOverride.Mode.STORM))
                        .then(Commands.literal("auto").executes(ctx -> clearOverrides(ctx.getSource()))))
                .then(Commands.literal("stats").executes(ctx -> showStats(ctx.getSource())))
                .then(Commands.literal("stress")
                        .then(Commands.argument(ARG_RADIUS, IntegerArgumentType.integer(0, 8))
                                .executes(ctx -> setStress(ctx.getSource(), IntegerArgumentType.getInteger(ctx, ARG_RADIUS)))))
                .then(Commands.literal("resample")
                        .executes(ctx -> resample(ctx.getSource(), ClimoraConfig.server().activeRadiusCells))
                        .then(Commands.argument(ARG_RADIUS, IntegerArgumentType.integer(0, 16))
                                .executes(ctx -> resample(ctx.getSource(), IntegerArgumentType.getInteger(ctx, ARG_RADIUS)))))
                .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource()))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> overrideCommand(String name, WeatherOverride.Mode mode) {
        return Commands.literal(name)
                .executes(ctx -> addOverride(ctx.getSource(), mode, DEFAULT_OVERRIDE_RADIUS, DEFAULT_OVERRIDE_SECONDS * 20))
                .then(Commands.argument(ARG_RADIUS, IntegerArgumentType.integer(0, 64))
                        .executes(ctx -> addOverride(ctx.getSource(), mode,
                                IntegerArgumentType.getInteger(ctx, ARG_RADIUS), DEFAULT_OVERRIDE_SECONDS * 20))
                        .then(Commands.argument(ARG_SECONDS, IntegerArgumentType.integer(1, 86400))
                                .executes(ctx -> addOverride(ctx.getSource(), mode,
                                        IntegerArgumentType.getInteger(ctx, ARG_RADIUS),
                                        IntegerArgumentType.getInteger(ctx, ARG_SECONDS) * 20))));
    }

    /**
     * Called by the vanilla {@code /weather} command. Returns true when the command was handled here.
     *
     * @param durationTicks vanilla duration, or -1 for a default
     */
    public static boolean redirectVanillaWeather(CommandSourceStack source, WeatherOverride.Mode mode, int durationTicks) {
        if (!ClimoraConfig.server().redirectWeatherCommand) {
            return false;
        }
        ClimateSimulation simulation = LocalWeather.view(source.getLevel()) instanceof ClimateSimulation local ? local : null;
        if (simulation == null) {
            return false;
        }
        int ticks = durationTicks > 0 ? durationTicks : VANILLA_DEFAULT_TICKS;
        addOverride(source, simulation, mode, VANILLA_WEATHER_RADIUS, ticks);
        return true;
    }

    private static BlockPos sourcePos(CommandContext<CommandSourceStack> context) {
        return BlockPos.containing(context.getSource().getPosition());
    }

    private static int addOverride(CommandSourceStack source, WeatherOverride.Mode mode, int radius, int ticks) {
        ClimateSimulation simulation = requireSimulation(source);
        if (simulation == null) {
            return 0;
        }
        addOverride(source, simulation, mode, radius, ticks);
        return 1;
    }

    private static void addOverride(CommandSourceStack source, ClimateSimulation simulation, WeatherOverride.Mode mode,
                                    int radius, int ticks) {
        BlockPos pos = BlockPos.containing(source.getPosition());
        int cellX = CellPos.blockToCell(pos.getX());
        int cellZ = CellPos.blockToCell(pos.getZ());
        simulation.addOverride(new WeatherOverride(mode, cellX, cellZ, radius, source.getLevel().getGameTime() + ticks));
        int blocks = (radius * 2 + 1) * SimulationConstants.CELL_SIZE_BLOCKS;
        send(source, format("Weather set to %s in %dx%d blocks around you for %d s. Use /climora weather auto to end it.",
                mode.name().toLowerCase(Locale.ROOT), blocks, blocks, ticks / 20), true);
    }

    private static int clearOverrides(CommandSourceStack source) {
        ClimateSimulation simulation = requireSimulation(source);
        if (simulation == null) {
            return 0;
        }
        BlockPos pos = BlockPos.containing(source.getPosition());
        int removed = simulation.clearOverrides(CellPos.blockToCell(pos.getX()), CellPos.blockToCell(pos.getZ()));
        send(source, format("Removed %d weather override(s) here. The simulation takes over again.", removed), true);
        return removed;
    }

    private static int showTemperature(CommandSourceStack source, BlockPos pos) {
        ClimateSimulation simulation = requireSimulation(source);
        if (simulation == null) {
            return 0;
        }

        ClimateInterpolation.Sample sample = simulation.sample(pos.getX() + 0.5, pos.getZ() + 0.5);
        if (sample == null) {
            source.sendFailure(Component.literal(format(
                    "No weather cells near %d %d %d yet. Cells are created around players.", pos.getX(), pos.getY(), pos.getZ())));
            return 0;
        }

        send(source, format("Air at %d %d %d: %.1f °C, %s", pos.getX(), pos.getY(), pos.getZ(),
                sample.airTemperature(pos.getY()), describePrecipitation(sample, pos.getY())), false);
        if (Float.isNaN(sample.surfaceElevation())) {
            send(source, format("Surface: %.1f °C (elevation unknown, from %d cells)",
                    sample.surfaceTemperature(), sample.cellCount()), false);
        } else {
            send(source, format("Surface: %.1f °C at Y %.0f (from %d cells)",
                    sample.surfaceTemperature(), sample.surfaceElevation(), sample.cellCount()), false);
        }
        send(source, format("Pressure %.0f hPa, humidity %.0f%%, clouds %.0f%%, %s",
                sample.pressure(), sample.relativeHumidity() * 100, sample.cloudCover() * 100,
                describeWind(sample.windX(), sample.windZ())), false);
        return 1;
    }

    private static int showCell(CommandSourceStack source, BlockPos pos) {
        ClimateSimulation simulation = requireSimulation(source);
        if (simulation == null) {
            return 0;
        }

        int cellX = CellPos.blockToCell(pos.getX());
        int cellZ = CellPos.blockToCell(pos.getZ());
        ClimateCell cell = simulation.grid().get(CellPos.key(cellX, cellZ));
        if (cell == null) {
            source.sendFailure(Component.literal(format("Cell (%d, %d) is not created yet.", cellX, cellZ)));
            return 0;
        }

        int size = SimulationConstants.CELL_SIZE_BLOCKS;
        long age = source.getLevel().getGameTime() - cell.lastUpdateTick();
        send(source, format("Cell (%d, %d): blocks x %d..%d, z %d..%d, updated %d ticks ago",
                cellX, cellZ, CellPos.minBlock(cellX), CellPos.minBlock(cellX) + size - 1,
                CellPos.minBlock(cellZ), CellPos.minBlock(cellZ) + size - 1, age), false);
        send(source, format("Temperature %.1f °C (target %.1f °C, daily mean %.1f °C, day/night ±%.1f °C), elevation %s",
                cell.temperature(), simulation.targetTemperature(cell), cell.baseTemperature(), cell.baseAmplitude(),
                cell.hasElevation() ? "Y " + cell.elevation() : "unknown"), false);
        send(source, format("Pressure %.1f hPa (lift %.2f), humidity %.0f%%, moisture %.1f g/kg",
                cell.pressure(), AtmosphereModel.lift(cell.pressure()), cell.relativeHumidity() * 100, cell.moisture()), false);
        send(source, format("Clouds %.0f%%, precipitation %.0f%%, storm %.0f%%, %s",
                cell.cloudCover() * 100, cell.precipitation() * 100, cell.storm() * 100,
                describeWind(cell.windX(), cell.windZ())), false);
        send(source, format("Biome humidity %.2f, water %.0f%%", cell.baseHumidity(), cell.waterFraction() * 100), false);
        WeatherOverride override = simulation.overrideAt(cellX, cellZ);
        if (override != null) {
            send(source, format("Forced weather: %s for %d more seconds", override.mode().name().toLowerCase(Locale.ROOT),
                    (override.untilTick() - source.getLevel().getGameTime()) / 20), false);
        }
        return 1;
    }

    private static int showStats(CommandSourceStack source) {
        ClimateSimulation simulation = requireSimulation(source);
        if (simulation == null) {
            return 0;
        }

        SimulationStats stats = simulation.stats();
        send(source, format("Climate of %s (data version %d, local weather %s)",
                source.getLevel().dimension().location(), ClimateGrid.DATA_VERSION,
                ClimoraConfig.server().localWeather ? "on" : "off"), false);
        send(source, format("Cells: %d stored, %d active, %d pending, %d queued for re-sampling%s",
                simulation.grid().size(), simulation.activeCellCount(), simulation.pendingCellCount(),
                simulation.pendingResampleCount(),
                simulation.stressRadius() > 0 ? ", stress radius +" + simulation.stressRadius() : ""), false);
        send(source, format("Tick time (last %d ticks): avg %.3f ms, max %.3f ms",
                SimulationStats.WINDOW_TICKS, stats.averageTickMillis(), stats.maxTickMillis()), false);
        send(source, format("Updates: %.1f cells/s, %d sampled, %d deferred, %d ticks over budget",
                stats.updatesPerSecond(), stats.cellsCreatedTotal(), stats.deferredUpdatesTotal(),
                stats.overBudgetTicksTotal()), false);
        send(source, format("Lightning strikes: %d", stats.lightningStrikesTotal()), false);
        return 1;
    }

    private static int setStress(CommandSourceStack source, int radius) {
        ClimateSimulation simulation = requireSimulation(source);
        if (simulation == null) {
            return 0;
        }
        simulation.setStressRadius(radius);
        int side = (ClimoraConfig.server().activeRadiusCells + radius) * 2 + 1;
        send(source, radius == 0
                ? "Stress test stopped."
                : format("Stress test: %dx%d cells around every player. Watch /climora stats.", side, side), true);
        return 1;
    }

    private static int resample(CommandSourceStack source, int radius) {
        ClimateSimulation simulation = requireSimulation(source);
        if (simulation == null) {
            return 0;
        }
        BlockPos pos = BlockPos.containing(source.getPosition());
        int queued = simulation.requestResample(CellPos.blockToCell(pos.getX()), CellPos.blockToCell(pos.getZ()), radius);
        send(source, format("Queued %d cells to be built from the terrain again (use after changing biomes).", queued), true);
        return queued;
    }

    private static int reload(CommandSourceStack source) {
        ClimoraConfig.loadServer();
        send(source, "Reloaded config/climora.json.", true);
        return 1;
    }

    private static String describePrecipitation(ClimateInterpolation.Sample sample, double y) {
        if (sample.precipitation() < LocalWeather.PRECIPITATION_THRESHOLD) {
            return "no precipitation";
        }
        String type = sample.precipitationType(y).name().toLowerCase(Locale.ROOT);
        return format("%s %.0f%%%s", type, sample.precipitation() * 100, sample.storm() > 0.15F ? " with thunderstorm" : "");
    }

    static String describeWind(float windX, float windZ) {
        float speed = (float) Math.sqrt(windX * windX + windZ * windZ);
        return format("wind %.1f m/s from %s (Beaufort %.0f)", speed, compass(-windX, -windZ), AtmosphereModel.beaufort(speed));
    }

    /** Compass name of a horizontal direction. North is -Z, east is +X. */
    static String compass(float x, float z) {
        if (x * x + z * z < 1.0E-4F) {
            return "nowhere";
        }
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        double degrees = Math.toDegrees(Math.atan2(x, -z));
        int index = (int) Math.floorMod(Math.round(degrees / 45.0), 8);
        return names[index];
    }

    @Nullable
    private static ClimateSimulation requireSimulation(CommandSourceStack source) {
        ClimateSimulation simulation = ClimateManager.get(source.getLevel());
        if (simulation == null) {
            source.sendFailure(Component.literal("No climate simulation in this dimension."));
        }
        return simulation;
    }

    private static void send(CommandSourceStack source, String message, boolean broadcastToOps) {
        source.sendSuccess(() -> Component.literal(message), broadcastToOps);
    }

    private static String format(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }
}
