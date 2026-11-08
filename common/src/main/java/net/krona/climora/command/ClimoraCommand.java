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
import net.krona.climora.climate.Forecast;
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
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * {@code /climora} commands, permission level 2:
 * <ul>
 *     <li>{@code temperature [pos]} — air temperature and weather at a position</li>
 *     <li>{@code cell [pos]} — full state of the weather cell at a position</li>
 *     <li>{@code forecast [pos]} — what the weather will do in the next hours</li>
 *     <li>{@code weather clear|cloudy|drizzle|rain|downpour|storm|freezing_rain [radius] [seconds]} — force
 *     weather around you; {@code weather auto} ends it</li>
 *     <li>{@code stats} — simulation performance and how often it rains</li>
 *     <li>{@code stress <radius>} — simulate extra cells around players for load tests, 0 to stop</li>
 *     <li>{@code resample [radius]} — build cells from the terrain again</li>
 *     <li>{@code reload} — reload {@code config/climora.json}</li>
 * </ul>
 * Every message is a translation key ({@code commands.climora.*}), so players read it in their language.
 * Numbers are formatted here, the same in every language.
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
    private static final String PREFIX = "commands.climora.";

    private ClimoraCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context,
                                Commands.CommandSelection selection) {
        LiteralArgumentBuilder<CommandSourceStack> weather = Commands.literal("weather");
        for (WeatherOverride.Mode mode : WeatherOverride.Mode.values()) {
            weather.then(overrideCommand(mode.name().toLowerCase(Locale.ROOT), mode));
        }
        weather.then(Commands.literal("auto").executes(ctx -> clearOverrides(ctx.getSource())));

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
                .then(Commands.literal("forecast")
                        .executes(ctx -> showForecast(ctx.getSource(), sourcePos(ctx)))
                        .then(Commands.argument(ARG_POS, BlockPosArgument.blockPos())
                                .executes(ctx -> showForecast(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, ARG_POS)))))
                .then(weather)
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
        send(source, tr("weather.set", modeName(mode), blocks, blocks, ticks / 20), true);
    }

    private static int clearOverrides(CommandSourceStack source) {
        ClimateSimulation simulation = requireSimulation(source);
        if (simulation == null) {
            return 0;
        }
        BlockPos pos = BlockPos.containing(source.getPosition());
        int removed = simulation.clearOverrides(CellPos.blockToCell(pos.getX()), CellPos.blockToCell(pos.getZ()));
        send(source, tr("weather.auto", removed), true);
        return removed;
    }

    private static int showTemperature(CommandSourceStack source, BlockPos pos) {
        ClimateSimulation simulation = requireSimulation(source);
        if (simulation == null) {
            return 0;
        }

        ClimateInterpolation.Sample sample = simulation.sample(pos.getX() + 0.5, pos.getZ() + 0.5);
        if (sample == null) {
            source.sendFailure(tr("no_cells", pos.getX(), pos.getY(), pos.getZ()));
            return 0;
        }

        send(source, tr("temperature.air", pos.getX(), pos.getY(), pos.getZ(),
                number("%.1f", simulation.airTemperature(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)),
                describePrecipitation(simulation, sample, pos)), false);
        if (Float.isNaN(sample.surfaceElevation())) {
            send(source, tr("temperature.surface_unknown", number("%.1f", sample.surfaceTemperature()), sample.cellCount()), false);
        } else {
            send(source, tr("temperature.surface", number("%.1f", sample.surfaceTemperature()),
                    number("%.0f", sample.surfaceElevation()), sample.cellCount()), false);
        }
        send(source, tr("temperature.air_state", number("%.0f", sample.pressure()), percent(sample.relativeHumidity()),
                percent(sample.cloudCover()), describeWind(sample.windX(), sample.windZ())), false);
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
            source.sendFailure(tr("cell.missing", cellX, cellZ));
            return 0;
        }

        int size = SimulationConstants.CELL_SIZE_BLOCKS;
        long age = source.getLevel().getGameTime() - cell.lastUpdateTick();
        send(source, tr("cell.area", cellX, cellZ, CellPos.minBlock(cellX), CellPos.minBlock(cellX) + size - 1,
                CellPos.minBlock(cellZ), CellPos.minBlock(cellZ) + size - 1, age), false);
        send(source, tr("cell.temperature", number("%.1f", cell.temperature()), number("%.1f", simulation.targetTemperature(cell)),
                number("%.1f", cell.baseTemperature()), number("%.1f", cell.baseAmplitude()),
                cell.hasElevation() ? tr("cell.elevation", cell.elevation()) : tr("cell.elevation_unknown")), false);
        send(source, tr("cell.air", number("%.1f", cell.pressure()), number("%.2f", AtmosphereModel.lift(cell.pressure())),
                percent(cell.relativeHumidity()), number("%.1f", cell.moisture())), false);
        send(source, tr("cell.weather", percent(cell.cloudCover()), percent(cell.precipitation()), percent(cell.storm()),
                percent(cell.hail()), describeWind(cell.windX(), cell.windZ())), false);
        send(source, tr("cell.ground", Float.isNaN(cell.columnTemperature()) ? "?" : number("%.1f", cell.columnTemperature()),
                percent(cell.wetness()), percent(cell.fog())), false);
        send(source, tr("cell.biome", number("%.2f", cell.baseHumidity()), percent(cell.waterFraction())), false);
        WeatherOverride override = simulation.overrideAt(cellX, cellZ);
        if (override != null) {
            send(source, tr("cell.forced", modeName(override.mode()),
                    (override.untilTick() - source.getLevel().getGameTime()) / 20), false);
        }
        return 1;
    }

    private static int showForecast(CommandSourceStack source, BlockPos pos) {
        ClimateSimulation simulation = requireSimulation(source);
        if (simulation == null) {
            return 0;
        }
        List<Forecast.Entry> entries = Forecast.forecast(simulation, pos);
        if (entries.isEmpty()) {
            source.sendFailure(tr("no_cells", pos.getX(), pos.getY(), pos.getZ()));
            return 0;
        }
        send(source, tr("forecast.header", pos.getX(), pos.getZ()), false);
        float nowPressure = entries.get(0).pressure();
        for (Forecast.Entry entry : entries) {
            MutableComponent when = entry.hours() == 0 ? tr("forecast.now") : tr("forecast.in", entry.hours());
            String trend = entry.hours() == 0 ? "" : entry.pressure() > nowPressure + 1.5F ? " ↑"
                    : entry.pressure() < nowPressure - 1.5F ? " ↓" : " →";
            MutableComponent line = tr("forecast.line", when, number("%.0f", entry.pressure()), trend,
                    tr("forecast.sky." + entry.sky().name().toLowerCase(Locale.ROOT)));
            if (entry.precipitation() != Forecast.Chance.NONE) {
                line.append(", ").append(tr("forecast.precipitation." + entry.precipitation().name().toLowerCase(Locale.ROOT),
                        precipitationName(entry.type())));
            }
            if (entry.thunderstorms()) {
                line.append(", ").append(tr("forecast.thunderstorms"));
            }
            send(source, line, false);
        }
        send(source, tr("forecast.note"), false);
        return entries.size();
    }

    private static int showStats(CommandSourceStack source) {
        ClimateSimulation simulation = requireSimulation(source);
        if (simulation == null) {
            return 0;
        }

        SimulationStats stats = simulation.stats();
        ClimoraConfig.Server config = ClimoraConfig.server();
        send(source, tr("stats.header", source.getLevel().dimension().location().toString(), ClimateGrid.DATA_VERSION,
                tr(config.localWeather ? "on" : "off"), number("%.2f", config.weatherSpeed)), false);
        send(source, tr("stats.cells", simulation.grid().size(), simulation.activeCellCount(), simulation.pendingCellCount(),
                simulation.pendingResampleCount(), simulation.stressRadius()), false);
        send(source, tr("stats.time", SimulationStats.WINDOW_TICKS, number("%.3f", stats.averageTickMillis()),
                number("%.3f", stats.maxTickMillis())), false);
        send(source, tr("stats.updates", number("%.1f", stats.updatesPerSecond()), stats.cellsCreatedTotal(),
                stats.deferredUpdatesTotal(), stats.overBudgetTicksTotal()), false);
        send(source, tr("stats.weather", share(stats.rainShare()), share(stats.stormShare()), share(stats.hailShare()),
                share(stats.freezingRainShare()), stats.lightningStrikesTotal()), false);
        return 1;
    }

    private static int setStress(CommandSourceStack source, int radius) {
        ClimateSimulation simulation = requireSimulation(source);
        if (simulation == null) {
            return 0;
        }
        simulation.setStressRadius(radius);
        int side = (ClimoraConfig.server().activeRadiusCells + radius) * 2 + 1;
        send(source, radius == 0 ? tr("stress.stopped") : tr("stress.started", side, side), true);
        return 1;
    }

    private static int resample(CommandSourceStack source, int radius) {
        ClimateSimulation simulation = requireSimulation(source);
        if (simulation == null) {
            return 0;
        }
        BlockPos pos = BlockPos.containing(source.getPosition());
        int queued = simulation.requestResample(CellPos.blockToCell(pos.getX()), CellPos.blockToCell(pos.getZ()), radius);
        send(source, tr("resample", queued), true);
        return queued;
    }

    private static int reload(CommandSourceStack source) {
        ClimoraConfig.loadServer();
        send(source, tr("reload"), true);
        return 1;
    }

    private static MutableComponent describePrecipitation(ClimateSimulation simulation, ClimateInterpolation.Sample sample, BlockPos pos) {
        if (sample.precipitation() < LocalWeather.PRECIPITATION_THRESHOLD) {
            return tr("precipitation.none");
        }
        AtmosphereModel.PrecipitationType type = LocalWeather.precipitationType(simulation, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        MutableComponent text = tr("precipitation.amount", precipitationName(type), percent(sample.precipitation()));
        if (sample.hail() >= ClimateSimulation.HAIL_THRESHOLD) {
            text.append(", ").append(tr("precipitation.hail"));
        }
        if (sample.storm() >= LocalWeather.THUNDERSTORM) {
            text.append(", ").append(tr("precipitation.thunderstorm"));
        }
        return text;
    }

    static MutableComponent precipitationName(AtmosphereModel.PrecipitationType type) {
        return Component.translatable("climora.precipitation." + type.name().toLowerCase(Locale.ROOT));
    }

    static MutableComponent modeName(WeatherOverride.Mode mode) {
        return Component.translatable("climora.weather_mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    static MutableComponent describeWind(float windX, float windZ) {
        float speed = (float) Math.sqrt(windX * windX + windZ * windZ);
        return tr("wind", number("%.1f", speed), compass(-windX, -windZ), number("%.0f", AtmosphereModel.beaufort(speed)));
    }

    /** Compass name of a horizontal direction, translated. North is -Z, east is +X. */
    static MutableComponent compass(float x, float z) {
        if (x * x + z * z < 1.0E-4F) {
            return Component.translatable("climora.compass.calm");
        }
        String[] names = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        double degrees = Math.toDegrees(Math.atan2(x, -z));
        int index = (int) Math.floorMod(Math.round(degrees / 45.0), 8);
        return Component.translatable("climora.compass." + names[index]);
    }

    @Nullable
    private static ClimateSimulation requireSimulation(CommandSourceStack source) {
        ClimateSimulation simulation = ClimateManager.get(source.getLevel());
        if (simulation == null) {
            source.sendFailure(tr("no_simulation"));
        }
        return simulation;
    }

    private static void send(CommandSourceStack source, Component message, boolean broadcastToOps) {
        source.sendSuccess(() -> message, broadcastToOps);
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }

    private static String number(String pattern, double value) {
        return String.format(Locale.ROOT, pattern, value);
    }

    private static String percent(float value) {
        return Float.isNaN(value) ? "?" : String.format(Locale.ROOT, "%.0f", value * 100.0F);
    }

    private static String share(double value) {
        return Double.isNaN(value) ? "?" : String.format(Locale.ROOT, "%.1f", value * 100.0);
    }
}
