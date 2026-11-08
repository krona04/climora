package net.krona.climora.client.dev;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import net.krona.climora.Climora;
import net.krona.climora.client.ClientWeather;
import net.krona.climora.client.CloudRenderer;
import net.krona.climora.climate.CellPos;
import net.krona.climora.climate.ClimateManager;
import net.krona.climora.climate.ClimateSimulation;
import net.krona.climora.config.ClimoraConfig;
import net.krona.climora.registry.ClimoraRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Development-only automated scenario: creates a fresh world, runs commands and takes screenshots,
 * so weather rendering can be checked without clicking through the game.
 * <p>
 * Does nothing unless the JVM is started with {@code -Dclimora.harness=<scenario>}, for example
 * {@code ./gradlew :fabric:runClient -PclimoraHarness=weather}. Screenshots are saved to
 * {@code run/screenshots/climora-*.png}.
 */
public final class DevHarness {
    private static final String PROPERTY = "climora.harness";
    private static final String WORLD_NAME = "climora-harness";
    private static final long WORLD_SEED = 20260916L;

    private static final List<Step> steps = new ArrayList<>();
    private static boolean worldRequested;
    private static int ticksInWorld;
    private static int nextStep;
    /** Test rigs placed during a scenario, in block coordinates. */
    private static BlockPos clearRig = BlockPos.ZERO;
    private static BlockPos rainRig = BlockPos.ZERO;
    private static BlockPos pit = BlockPos.ZERO;
    private static double clockStart;
    /** World type of the harness world; null is the default one. */
    private static ResourceKey<WorldPreset> preset;
    private static long gameStart;

    private record Step(int tick, String description, java.util.function.Consumer<Minecraft> action) {
    }

    private DevHarness() {
    }

    public static void init() {
        String scenario = System.getProperty(PROPERTY);
        if (scenario == null || scenario.isBlank()) {
            return;
        }
        switch (scenario) {
            case "weather" -> buildWeatherScenario();
            case "snow" -> buildSnowScenario();
            case "precip" -> buildPrecipitationScenario();
            case "ice" -> buildIceScenario();
            case "climate" -> buildClimateScenario();
            case "jungle" -> buildJungleClimateScenario();
            case "distant" -> buildDistantScenario();
            case "winter" -> buildWinterScenario();
            case "belts" -> buildBeltsScenario();
            case "particlerain" -> buildParticleRainScenario();
            case "hail" -> buildHailScenario();
            case "rainsound" -> buildRainSoundScenario();
            case "hailedge" -> buildHailEdgeScenario();
            default -> {
                Climora.LOGGER.error("[Harness] Unknown scenario '{}'. Use weather, snow, precip, ice, climate, jungle, distant, winter, belts, particlerain, hail, rainsound or hailedge.", scenario);
                return;
            }
        }
        Climora.LOGGER.info("[Harness] Running scenario '{}'", scenario);
        ClientGuiEvent.INIT_POST.register((screen, access) -> {
            if (screen instanceof TitleScreen && !worldRequested) {
                worldRequested = true;
                createWorld(Minecraft.getInstance());
            }
        });
        ClientTickEvent.CLIENT_POST.register(DevHarness::tick);
    }

    private static void buildWeatherScenario() {
        action(1, "small GUI", minecraft -> {
            // Fit the whole F3 screen into the default 854x480 window.
            minecraft.options.guiScale().set(1);
            minecraft.resizeDisplay();
        });
        command(20, "gamerule doDaylightCycle false");
        command(21, "gamerule doMobSpawning false");
        command(22, "time set 4000");
        action(25, "fly", minecraft -> {
            minecraft.player.getAbilities().flying = true;
            minecraft.player.onUpdateAbilities();
        });
        // Negative pitch looks up.
        command(30, "execute positioned over motion_blocking run tp @s ~ ~30 ~ -45 -35");
        command(40, "climora weather clear 5 3000");
        screenshot(300, "01-clear");

        command(310, "climora weather cloudy 5 3000");
        screenshot(700, "02-cloudy");
        // The creative inventory used to crash while building its tabs.
        action(710, "open creative inventory", minecraft -> minecraft.setScreen(new CreativeModeInventoryScreen(
                minecraft.player, minecraft.player.connection.enabledFeatures(), minecraft.options.operatorItemsTab().get())));
        screenshot(730, "03-creative-inventory");
        action(740, "close inventory", minecraft -> minecraft.setScreen(null));

        command(710, "climora weather rain 5 3000");
        command(715, "execute positioned over motion_blocking run tp @s ~ ~2 ~ -45 0");
        screenshot(1100, "04-rain");
        command(1105, "tp @s ~ ~ ~ -45 -80");
        screenshot(1125, "04b-rain-zenith");
        command(1130, "tp @s ~ ~ ~ -45 0");
        action(1110, "F3 on", minecraft -> minecraft.getDebugOverlay().toggleOverlay());
        screenshot(1150, "05-rain-f3");
        action(1160, "F3 off", minecraft -> minecraft.getDebugOverlay().toggleOverlay());
        command(1161, "climora stats");
        command(1162, "climora cell");
        command(1163, "climora temperature");

        command(1170, "climora weather storm 5 3000");
        command(1175, "tp @s ~ ~20 ~ -45 -25");
        screenshot(1550, "06-storm");
        command(1555, "tp @s ~ ~ ~ -45 -80");
        screenshot(1575, "06b-storm-zenith");
        command(1580, "execute positioned ~4 ~ ~ positioned over motion_blocking run setblock ~ ~ ~ climora:weather_vane");
        command(1585, "execute positioned ~4 ~ ~ positioned over motion_blocking run tp @s ~-2.5 ~0.3 ~-1.5 facing ~0.5 ~-0.2 ~0.5");
        screenshot(1600, "07-weather-vane");

        command(1610, "climora weather auto");
        command(1611, "climora weather clear 8 3000");
        command(1612, "climora weather rain 1 3000");
        // Rain covers cells -1..1 (x < 256). At x=305 the rain fades out; facing north, rain is on the left.
        // Teleport high first: "positioned over" needs the target chunk to be loaded.
        command(1620, "tp @s 305 120 64 180 5");
        command(1700, "execute positioned over motion_blocking run tp @s ~ ~2 ~ 180 5");
        screenshot(2050, "08-rain-edge");
        command(2055, "climora stats");

        // Pausing freezes the integrated server, so no weather payloads arrive. The client used to give up
        // after five seconds and fall back to vanilla clouds. Hold the pause screen for well over that.
        action(2070, "pause", minecraft -> minecraft.setScreen(new PauseScreen(true)));
        action(2090, "check paused", DevHarness::reportWeatherState);
        action(2250, "check still local after 9 s paused", DevHarness::reportWeatherState);
        action(2255, "unpause", minecraft -> minecraft.setScreen(null));
        action(2275, "check after unpause", DevHarness::reportWeatherState);
        action(2290, "quit", Minecraft::stop);
    }

    /** Logs whether the client still has local weather, for the pause check. */
    private static void reportWeatherState(Minecraft minecraft) {
        Climora.LOGGER.info("[Harness] paused={} localWeather={} vanillaClouds={}",
                minecraft.isPaused(), ClientWeather.INSTANCE.isActive(), !CloudRenderer.replacesVanilla());
    }

    /**
     * Precipitation of 0.2: drizzle and downpour, hail in a warm thunderstorm, rain putting out a fire next
     * to a control fire under a clear sky, a distant shower, puddles filling a pit and drying out, night fog,
     * and the forecast.
     */
    private static void buildPrecipitationScenario() {
        action(1, "small GUI", minecraft -> {
            minecraft.options.guiScale().set(1);
            minecraft.resizeDisplay();
        });
        command(20, "gamerule doDaylightCycle false");
        command(21, "gamerule doMobSpawning false");
        command(22, "time set 4000");
        action(25, "fly", minecraft -> {
            minecraft.player.getAbilities().flying = true;
            minecraft.player.onUpdateAbilities();
        });
        command(30, "execute positioned over motion_blocking run tp @s ~ ~1.6 ~ -45 8");

        command(40, "climora weather drizzle 4 30000");
        command(45, "tick sprint 600");
        screenshot(160, "p01-drizzle");
        command(165, "climora temperature");
        action(166, "client weather", DevHarness::reportClientWeather);
        command(167, "tp @s ~ ~ ~ -45 -80");
        screenshot(180, "p01b-drizzle-zenith");
        command(182, "tp @s ~ ~ ~ -45 8");

        command(170, "climora weather downpour 4 30000");
        command(175, "tick sprint 600");
        screenshot(290, "p02-downpour");
        command(295, "climora temperature");

        command(300, "climora weather hail 4 30000");
        command(305, "tick sprint 600");
        command(420, "tp @s ~ ~ ~ -45 45");
        screenshot(440, "p03-hail");
        command(445, "climora cell");
        command(447, "tp @s ~ ~ ~ -45 8");

        // Rain puts out fire: a burning plank floor in a clear cell and one in a raining cell next to it.
        command(450, "gamerule doFireTick true");
        action(452, "fire rigs", minecraft -> {
            BlockPos player = minecraft.player.blockPosition();
            int cellX = CellPos.blockToCell(player.getX());
            int cellZ = CellPos.blockToCell(player.getZ());
            clearRig = new BlockPos(CellPos.centerBlock(cellX), 150, CellPos.centerBlock(cellZ));
            rainRig = new BlockPos(CellPos.centerBlock(cellX + 1), 150, CellPos.centerBlock(cellZ));
            send(minecraft, String.format("execute positioned %d 150 %d run climora weather clear 0 30000", clearRig.getX(), clearRig.getZ()));
            send(minecraft, String.format("execute positioned %d 150 %d run climora weather rain 0 30000", rainRig.getX(), rainRig.getZ()));
        });
        command(455, "tick sprint 300");
        onServer(520, "light fires", level -> {
            buildFireRig(level, clearRig);
            buildFireRig(level, rainRig);
            return "5x5 burning plank floors at " + clearRig.toShortString() + " (clear) and " + rainRig.toShortString() + " (rain)";
        });
        command(525, "tick sprint 400");
        onServer(600, "fire under a clear sky", level -> fireReport(level, clearRig));
        onServer(601, "fire under rain", level -> fireReport(level, rainRig));

        // A distant shower: clear here, rain in the next cell east, from about 80 to 300 blocks away. Looking east.
        command(610, "climora weather auto");
        command(611, "climora weather clear 6 30000");
        command(612, "execute positioned ~180 ~ ~ run climora weather downpour 0 30000");
        command(615, "tick sprint 600");
        command(730, "tp @s ~ ~25 ~ -90 4");
        screenshot(760, "p04-distant-rain");
        action(762, "client weather", DevHarness::reportClientWeather);
        command(765, "execute positioned over motion_blocking run tp @s ~ ~1.6 ~ -45 8");

        // Puddles: steady rain on a pit dug into natural ground, then a dry spell.
        command(770, "climora weather rain 3 30000");
        onServer(775, "dig pit", level -> {
            BlockPos player = BlockPos.containing(Minecraft.getInstance().player.position());
            pit = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, player.offset(6, 0, 6));
            int dug = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pit.offset(dx, 0, dz)).below();
                    level.setBlockAndUpdate(top, Blocks.AIR.defaultBlockState());
                    dug++;
                }
            }
            return dug + " columns dug around " + pit.toShortString();
        });
        command(780, "tick sprint 8000");
        onServer(940, "wet ground", level -> "wetness " + String.format(java.util.Locale.ROOT, "%.2f", simulation(level).wetness(pit.getX() + 0.5, pit.getZ() + 0.5)));
        onServer(941, "puddles after rain", level -> countAround(level, pit, 4, 3, state -> state.is(ClimoraRegistries.PUDDLE.get())));
        action(945, "look into the pit", minecraft -> send(minecraft,
                String.format("tp @s %d.5 %d %d.5 facing %d.5 %d %d.5", pit.getX() - 4, pit.getY() + 3, pit.getZ() - 4,
                        pit.getX(), pit.getY() - 1, pit.getZ())));
        onServer(946, "puddle positions", level -> {
            StringBuilder found = new StringBuilder();
            for (BlockPos pos : BlockPos.betweenClosed(pit.offset(-4, -3, -4), pit.offset(4, 3, 4))) {
                if (level.getBlockState(pos).is(ClimoraRegistries.PUDDLE.get())) {
                    found.append(' ').append(pos.getX() - pit.getX()).append(',').append(pos.getY() - pit.getY()).append(',').append(pos.getZ() - pit.getZ());
                }
            }
            return found.toString();
        });
        screenshot(970, "p05-puddles");
        command(975, "climora weather clear 3 30000");
        command(978, "tick sprint 12000");
        onServer(1160, "wet ground after the dry spell", level -> "wetness " + String.format(java.util.Locale.ROOT, "%.2f", simulation(level).wetness(pit.getX() + 0.5, pit.getZ() + 0.5)));
        onServer(1161, "puddles after the dry spell", level -> countAround(level, pit, 4, 3, state -> state.is(ClimoraRegistries.PUDDLE.get())));

        // Radiation fog: forms on a still, humid night, is seen at sunrise before the sun burns it off.
        command(1170, "time set 18000");
        command(1171, "climora weather fog 4 30000");
        command(1175, "tick sprint 4000");
        command(1290, "time set 23600");
        command(1292, "execute positioned over motion_blocking run tp @s ~ ~1.6 ~ -45 3");
        screenshot(1320, "p06-fog");
        command(1325, "climora cell");
        action(1326, "client weather", DevHarness::reportClientWeather);

        command(1330, "time set 4000");
        command(1331, "climora weather auto");
        command(1335, "climora forecast");
        command(1340, "climora stats");

        // weatherSpeed runs the weather clock faster than game time.
        onServer(1345, "weather clock", level -> {
            clockStart = simulation(level).weatherTime();
            gameStart = level.getGameTime();
            ClimoraConfig.server().weatherSpeed = 4.0;
            return String.format(java.util.Locale.ROOT, "weather time %.0f, speed set to x4", clockStart);
        });
        command(1347, "tick sprint 400");
        onServer(1420, "weather clock at x4", level -> {
            double weather = simulation(level).weatherTime() - clockStart;
            long game = level.getGameTime() - gameStart;
            ClimoraConfig.server().weatherSpeed = 1.0;
            return String.format(java.util.Locale.ROOT, "%.0f weather ticks in %d game ticks (x%.2f)", weather, game, weather / game);
        });
        action(1440, "quit", Minecraft::stop);
    }

    /**
     * Natural weather, nothing forced: five in-game days run fast, then how much of the time it rained,
     * stormed, hailed and froze in the cells around the spawn.
     */
    private static void buildClimateScenario() {
        command(20, "gamerule doMobSpawning false");
        command(25, "tick sprint 120000");
        for (int minute = 1; minute <= 4; minute++) {
            command(minute * 1200, "climora stats");
        }
        command(4810, "climora forecast");
        action(4830, "quit", Minecraft::stop);
    }

    /**
     * A downpour in cell (2, 0), x 256..383, with clear sky everywhere else, seen from the west at three
     * distances. Fixed coordinates, so every run shows the same thing; no GUI in the shots.
     */
    private static void buildDistantScenario() {
        action(1, "small GUI", minecraft -> {
            minecraft.options.guiScale().set(1);
            minecraft.resizeDisplay();
        });
        command(20, "gamerule doDaylightCycle false");
        command(21, "gamerule doMobSpawning false");
        command(22, "time set 4000");
        // A spectator does not fall while the time runs fast, and has no hand in the shot.
        command(25, "gamemode spectator");
        command(30, "tp @s 100 110 64 -90 -6");
        command(60, "climora weather clear 6 30000");
        command(61, "execute positioned 320 110 64 run climora weather downpour 0 30000");
        command(65, "tick sprint 600");
        action(180, "hide GUI", minecraft -> minecraft.options.hideGui = true);
        screenshot(190, "d01-near");
        command(195, "tp @s 30 100 64 -90 -4");
        screenshot(240, "d02-far");
        command(245, "tp @s 150 140 40 -70 12");
        screenshot(290, "d03-above");
        command(295, "execute positioned 120 0 64 positioned over motion_blocking run tp @s ~ ~2 ~ -90 -10");
        screenshot(340, "d04-ground");
        action(345, "client weather", DevHarness::reportClientWeather);
        action(360, "quit", Minecraft::stop);
    }

    /**
     * Snowy biomes stay snowy: the nearest snowy plains under a clear sky at the warmest hour of the day,
     * for half a day. Counts the snow layers before and after, in the middle and with the edges.
     */
    private static void buildWinterScenario() {
        command(20, "gamerule doDaylightCycle false");
        command(21, "gamerule doMobSpawning false");
        command(22, "time set 9000");
        command(23, "gamemode spectator");
        onServer(25, "find snowy plains", level -> {
            var found = level.findClosestBiome3d(biome -> biome.is(net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS),
                    playerPos(), 6400, 64, 64);
            if (found == null) {
                return "no snowy plains within 6400 blocks";
            }
            pit = found.getFirst();
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),
                    String.format("tp %s %d 160 %d", Minecraft.getInstance().player.getGameProfile().getName(), pit.getX(), pit.getZ()));
            return "snowy plains at " + pit.toShortString();
        });
        command(200, "climora weather clear 4 60000");
        onServer(210, "snow before (middle 48x48)", level -> countSnow(level, pit, 24));
        onServer(211, "snow before (with edges 160x160)", level -> countSnow(level, pit, 80));
        command(215, "tick sprint 12000");
        onServer(560, "snow after half a sunny day (middle 48x48)", level -> countSnow(level, pit, 24));
        onServer(561, "snow after half a sunny day (with edges 160x160)", level -> countSnow(level, pit, 80));
        action(565, "temperature at the snow", minecraft -> send(minecraft,
                String.format("execute positioned %d 0 %d positioned over motion_blocking run climora temperature ~ ~ ~", pit.getX(), pit.getZ())));
        action(566, "cell", minecraft -> send(minecraft, String.format("climora cell %d 70 %d", pit.getX(), pit.getZ())));
        action(600, "quit", Minecraft::stop);
    }

    /**
     * The "Climate belts" world type: biomes sampled along the meridian from the equator past the pole
     * (straight from the biome source, no chunks generated), and views from above at the equator, the
     * subtropics, the middle latitudes and the pole.
     */
    private static void buildBeltsScenario() {
        preset = ResourceKey.create(Registries.WORLD_PRESET, ResourceLocation.fromNamespaceAndPath(Climora.MOD_ID, "climate_belts"));
        action(1, "small GUI", minecraft -> {
            minecraft.options.guiScale().set(1);
            minecraft.resizeDisplay();
        });
        command(20, "gamerule doDaylightCycle false");
        command(21, "gamerule doMobSpawning false");
        command(22, "time set 6000");
        command(23, "gamemode spectator");
        command(24, "climora weather clear 8 60000");
        onServer(25, "listed with the normal world types", level -> String.valueOf(level.registryAccess()
                .registryOrThrow(Registries.WORLD_PRESET).getTagOrEmpty(WorldPresetTags.NORMAL).iterator().hasNext()
                && level.registryAccess().registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(preset)
                        .is(WorldPresetTags.NORMAL)));
        for (int z = 0; z <= 12000; z += 500) {
            int line = z;
            onServer(30, "z=" + line, level -> beltReport(level, line));
        }
        onServer(31, "southern hemisphere z=-5000", level -> beltReport(level, -5000));
        onServer(31, "southern hemisphere z=-10500", level -> beltReport(level, -10500));
        action(40, "hide GUI", minecraft -> minecraft.options.hideGui = true);
        int tick = 40;
        for (int z : new int[] {0, 2500, 4500, 7200, 10500}) {
            command(tick, "tp @s 0 230 " + z + " 0 55");
            screenshot(tick + 240, "b-z" + z);
            tick += 250;
        }
        action(tick + 10, "quit", Minecraft::stop);
    }

    /**
     * The surface biomes along a line of latitude, 64 points 8000 blocks wide: the share where it snows, the
     * share of hot ones, the commonest, and every one that is not frozen.
     */
    private static String beltReport(ServerLevel level, int z) {
        var generator = level.getChunkSource().getGenerator();
        var randomState = level.getChunkSource().randomState();
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        java.util.Map<String, Integer> thawed = new java.util.TreeMap<>();
        int frozen = 0;
        int hot = 0;
        int points = 64;
        for (int i = 0; i < points; i++) {
            int x = -4000 + i * 125;
            int y = Math.max(generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState), level.getSeaLevel());
            var biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                    randomState.sampler());
            String name = biome.unwrapKey().map(key -> key.location().getPath()).orElse("?");
            counts.merge(name, 1, Integer::sum);
            BlockPos surface = new BlockPos(x, y, z);
            if (biome.value().coldEnoughToSnow(surface) || name.contains("frozen")) {
                frozen++;
            } else {
                thawed.merge(name, 1, Integer::sum);
            }
            if (biome.value().getBaseTemperature() >= 1.0F) {
                hot++;
            }
        }
        StringBuilder top = new StringBuilder();
        counts.entrySet().stream().sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed()).limit(4)
                .forEach(entry -> top.append(' ').append(entry.getKey()).append('=').append(entry.getValue()));
        return String.format(java.util.Locale.ROOT, "frozen %d%%, hot %d%%,%s | not frozen: %s",
                frozen * 100 / points, hot * 100 / points, top, thawed);
    }

    /**
     * Rain sounds around a downpour in cell (2, 0) with clear sky around it: inside, at its edge, about 30 blocks
     * from it and far away. Every weather sound is logged with its event, volume and distance from the camera.
     * With Sound Physics Remastered ({@code -PclimoraCompat=soundphysics}) the events should be Climora's spatial ones.
     */
    private static void buildRainSoundScenario() {
        action(1, "listen to weather sounds", minecraft -> minecraft.getSoundManager().addListener((sound, events, range) -> {
            String id = sound.getLocation().toString();
            if (id.contains("rain") || id.contains("hail")) {
                double dx = sound.getX() - minecraft.player.getX();
                double dz = sound.getZ() - minecraft.player.getZ();
                weatherSounds.add(String.format(java.util.Locale.ROOT, "%s v%.2f p%.2f %+.0f/%+.0f", id, sound.getVolume(), sound.getPitch(), dx, dz));
            }
        }));
        command(20, "gamerule doDaylightCycle false");
        command(21, "gamerule doMobSpawning false");
        command(22, "time set 6000");
        command(23, "gamemode spectator");
        command(25, "execute positioned 320 110 64 run climora weather clear 6 30000");
        command(27, "execute positioned 320 110 64 run climora weather downpour 0 30000");
        command(28, "tp @s 320 120 64 -90 10");
        command(30, "tick sprint 600");
        String[][] spots = {{"inside the downpour", "320"}, {"dry, rain from ~11 blocks east", "192"},
                {"dry, rain from ~33 blocks east", "170"}, {"dry, rain ~80 blocks east", "120"}};
        int tick = 200;
        for (String[] spot : spots) {
            command(tick, "execute positioned " + spot[1] + " 0 64 positioned over motion_blocking run tp @s ~ ~2 ~ -90 5");
            action(tick + 20, "start listening: " + spot[0], minecraft -> weatherSounds.clear());
            action(tick + 120, "weather sounds: " + spot[0], minecraft -> {
                java.util.Map<String, Integer> byEvent = new java.util.TreeMap<>();
                weatherSounds.forEach(line -> byEvent.merge(line.substring(0, line.indexOf(' ')), 1, Integer::sum));
                Climora.LOGGER.info("[Harness] weather sounds, {}: {} in 100 ticks {}; e.g. {}", spot[0], weatherSounds.size(), byEvent,
                        weatherSounds.stream().limit(3).toList());
            });
            tick += 140;
        }
        action(tick + 10, "quit", Minecraft::stop);
    }

    private static final List<String> weatherSounds = new ArrayList<>();

    /**
     * Hail forced in cell (2, 0) with clear sky around it, seen from its middle, its edge and the faint tail
     * the blending between cells leaves outside it. Logs the hail the client knows at the camera and how many
     * shards hit the ground and hail sounds played in 100 ticks: where the stones are too faint to see, neither.
     */
    private static void buildHailEdgeScenario() {
        action(1, "listen to weather sounds", minecraft -> minecraft.getSoundManager().addListener((sound, events, range) -> {
            if (sound.getLocation().getPath().contains("hail")) {
                weatherSounds.add(sound.getLocation().toString());
            }
        }));
        command(20, "gamerule doDaylightCycle false");
        command(21, "gamerule doMobSpawning false");
        command(22, "time set 6000");
        command(23, "gamemode spectator");
        command(25, "execute positioned 320 110 64 run climora weather clear 6 30000");
        command(27, "execute positioned 320 110 64 run climora weather hail 0 30000");
        command(28, "tp @s 320 120 64 -90 30");
        command(30, "tick sprint 600");
        int tick = 200;
        for (int x : new int[] {320, 270, 240, 220, 200}) {
            command(tick, "execute positioned " + x + " 0 64 positioned over motion_blocking run tp @s ~ ~2 ~ -90 30");
            action(tick + 20, "start counting at x=" + x, minecraft -> {
                weatherSounds.clear();
                shardsAtStart = countShards(minecraft);
                shardsSeen = 0;
            });
            for (int t = tick + 21; t < tick + 120; t++) {
                action(t, "count shards", minecraft -> shardsSeen = Math.max(shardsSeen, countShards(minecraft)));
            }
            action(tick + 120, "hail at x=" + x, minecraft -> Climora.LOGGER.info(
                    "[Harness] hail at x={}: client hail {} at the camera, up to {} shards on the ground at once, {} hail sounds in 100 ticks",
                    x, String.format(java.util.Locale.ROOT, "%.2f",
                            ClientWeather.INSTANCE.hail(minecraft.player.getX(), minecraft.player.getZ())),
                    shardsSeen, weatherSounds.size()));
            tick += 140;
        }
        action(tick + 10, "quit", Minecraft::stop);
    }

    private static int shardsAtStart;
    private static int shardsSeen;

    /** Snowball shards currently alive in the particle engine: the shards of hail on the ground. Dev only. */
    @SuppressWarnings("unchecked")
    private static int countShards(Minecraft minecraft) {
        try {
            java.lang.reflect.Field particles = net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");
            particles.setAccessible(true);
            int count = 0;
            for (java.util.Collection<?> queue : ((java.util.Map<?, ? extends java.util.Collection<?>>) particles.get(minecraft.particleEngine)).values()) {
                for (Object particle : queue) {
                    if (particle instanceof net.minecraft.client.particle.BreakingItemParticle) {
                        count++;
                    }
                }
            }
            return count;
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    /**
     * How often a thunderstorm hails: a strong storm forced over all 7×7 active cells at the warmest hour for 12000 ticks,
     * then {@code /climora stats}; then forced hail over the camera, to hear it (with Sound Physics Remastered:
     * {@code -PclimoraCompat=soundphysics}).
     */
    private static void buildHailScenario() {
        command(20, "gamerule doDaylightCycle false");
        command(21, "gamerule doMobSpawning false");
        // The warmest hour of the day: the most a storm can get.
        command(22, "time set 9000");
        command(23, "gamemode spectator");
        command(25, "climora weather storm 3 60000");
        command(30, "tick sprint 12000");
        action(900, "stats after 12000 ticks of storm", minecraft -> send(minecraft, "climora stats"));
        action(901, "cell", minecraft -> send(minecraft, "climora cell"));
        command(905, "climora weather hail 0 60000");
        command(910, "tick sprint 600");
        action(1020, "cell in forced hail", minecraft -> send(minecraft, "climora cell"));
        action(1040, "quit", Minecraft::stop);
    }

    /**
     * With Particle Rain installed ({@code -PclimoraCompat=particlerain}): a downpour in cell (2, 0) with clear sky
     * around it, seen from inside, from the dry side of its edge and as a drizzle, then rain forced over the nearest
     * snowy plains. Counts Particle Rain's particles by kind, under local precipitation and outside it.
     */
    private static void buildParticleRainScenario() {
        action(1, "small GUI", minecraft -> {
            minecraft.options.guiScale().set(1);
            minecraft.resizeDisplay();
        });
        command(20, "gamerule doDaylightCycle false");
        command(21, "gamerule doMobSpawning false");
        command(22, "time set 6000");
        command(23, "gamemode spectator");
        command(25, "execute positioned 320 110 64 run climora weather clear 6 30000");
        // A fixed height first: "over motion_blocking" fails while the chunk is not loaded.
        command(28, "tp @s 320 120 64 -90 10");
        command(60, "execute positioned 320 0 64 positioned over motion_blocking run tp @s ~ ~3 ~ -90 10");
        action(80, "particles, clear sky", minecraft -> logParticles("clear sky"));
        command(85, "execute positioned 320 110 64 run climora weather downpour 0 30000");
        command(90, "tick sprint 600");
        action(250, "hide GUI", minecraft -> minecraft.options.hideGui = true);
        action(260, "particles, inside the downpour", minecraft -> logParticles("inside the downpour"));
        screenshot(261, "pr01-inside");
        command(270, "execute positioned 192 0 64 positioned over motion_blocking run tp @s ~ ~3 ~ -90 5");
        action(360, "particles, dry side of the edge", minecraft -> logParticles("dry side of the edge"));
        action(361, "precipitation east of the camera", minecraft -> {
            double x = minecraft.player.getX();
            double z = minecraft.player.getZ();
            StringBuilder line = new StringBuilder();
            for (int dx = 0; dx <= 40; dx += 8) {
                line.append(String.format(java.util.Locale.ROOT, " +%d: %.2f", dx, ClientWeather.INSTANCE.precipitation(x + dx, z)));
            }
            Climora.LOGGER.info("[Harness] precipitation east of the camera:{}", line);
        });
        screenshot(362, "pr02-edge");
        command(370, "execute positioned 320 110 64 run climora weather drizzle 0 30000");
        command(372, "tick sprint 600");
        command(375, "execute positioned 320 0 64 positioned over motion_blocking run tp @s ~ ~3 ~ -90 10");
        action(540, "particles, drizzle", minecraft -> logParticles("inside the drizzle"));
        screenshot(541, "pr03-drizzle");
        onServer(550, "find snowy plains", level -> {
            var found = level.findClosestBiome3d(biome -> biome.is(net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS),
                    playerPos(), 6400, 64, 64);
            if (found == null) {
                return "no snowy plains within 6400 blocks";
            }
            pit = found.getFirst();
            return "snowy plains at " + pit.toShortString();
        });
        action(570, "rain over the snowy plains", minecraft -> {
            send(minecraft, String.format("tp @s %d 140 %d -90 10", pit.getX(), pit.getZ()));
            send(minecraft, String.format("execute positioned %d 110 %d run climora weather rain 1 30000", pit.getX(), pit.getZ()));
            send(minecraft, "tick sprint 600");
        });
        action(700, "down to the ground", minecraft -> send(minecraft, String.format(
                "execute positioned %d 0 %d positioned over motion_blocking run tp @s ~ ~3 ~ -90 10", pit.getX(), pit.getZ())));
        action(760, "air at the snowy plains", minecraft -> send(minecraft, "climora temperature ~ ~ ~"));
        action(770, "particles, snowy plains", minecraft -> logParticles("rain forced over snowy plains"));
        screenshot(771, "pr04-snow");
        action(780, "client weather", DevHarness::reportClientWeather);
        action(790, "quit", Minecraft::stop);
    }

    /**
     * Particle Rain's particles by kind, split by whether the local weather has precipitation where each one is.
     * Reflection, so this compiles and runs without Particle Rain; dev only, where the fields have Mojang's names.
     */
    @SuppressWarnings("unchecked")
    private static void logParticles(String when) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            int total = Class.forName("pigcart.particlerain.ParticleSpawner").getField("particleCount").getInt(null);
            java.lang.reflect.Field particles = net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");
            java.lang.reflect.Field fieldX = net.minecraft.client.particle.Particle.class.getDeclaredField("x");
            java.lang.reflect.Field fieldZ = net.minecraft.client.particle.Particle.class.getDeclaredField("z");
            particles.setAccessible(true);
            fieldX.setAccessible(true);
            fieldZ.setAccessible(true);
            java.util.Map<String, int[]> kinds = new java.util.TreeMap<>();
            for (java.util.Collection<?> queue : ((java.util.Map<?, ? extends java.util.Collection<?>>) particles.get(minecraft.particleEngine)).values()) {
                for (Object particle : queue) {
                    if (!particle.getClass().getName().startsWith("pigcart.")) {
                        continue;
                    }
                    String kind = particle.getClass().getSimpleName();
                    try {
                        Object data = particle.getClass().getField("data").get(particle);
                        kind = (String) data.getClass().getField("id").get(data);
                    } catch (NoSuchFieldException ignored) {
                        // Not a configurable particle (ripples, streaks): keep the class name.
                    }
                    boolean wet = ClientWeather.INSTANCE.precipitation(fieldX.getDouble(particle), fieldZ.getDouble(particle))
                            >= net.krona.climora.weather.LocalWeather.PRECIPITATION_THRESHOLD;
                    kinds.computeIfAbsent(kind, key -> new int[2])[wet ? 0 : 1]++;
                }
            }
            StringBuilder line = new StringBuilder();
            kinds.forEach((kind, count) -> line.append(' ').append(kind).append('=').append(count[0]).append('/').append(count[1]));
            Climora.LOGGER.info("[Harness] Particle Rain, {}: {} particles; by kind (under precipitation/outside):{}",
                    when, total, line);
        } catch (ReflectiveOperationException e) {
            Climora.LOGGER.info("[Harness] Particle Rain, {}: not installed ({})", when, e.toString());
        }
    }

    /** Snow layers and snow blocks on the surface in a square around a position. */
    private static String countSnow(ServerLevel level, BlockPos center, int radius) {
        int layers = 0;
        int columns = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, center.offset(dx, 0, dz));
                BlockState state = level.getBlockState(top);
                if (state.is(Blocks.SNOW)) {
                    layers += state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS);
                    columns++;
                }
            }
        }
        return columns + " columns with snow, " + layers + " layers";
    }

    /** The same in the nearest jungle, where warm, humid air should bring thunderstorms on its own. */
    private static void buildJungleClimateScenario() {
        command(20, "gamerule doMobSpawning false");
        onServer(25, "find a jungle", level -> {
            var found = level.findClosestBiome3d(biome -> biome.is(net.minecraft.world.level.biome.Biomes.JUNGLE),
                    playerPos(), 6400, 64, 64);
            if (found == null) {
                return "no jungle within 6400 blocks";
            }
            BlockPos pos = found.getFirst();
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),
                    String.format("tp %s %d 200 %d", Minecraft.getInstance().player.getGameProfile().getName(), pos.getX(), pos.getZ()));
            return "jungle at " + pos.toShortString();
        });
        action(30, "fly", minecraft -> {
            minecraft.player.getAbilities().flying = true;
            minecraft.player.onUpdateAbilities();
        });
        command(200, "tick sprint 72000");
        for (int minute = 1; minute <= 3; minute++) {
            command(200 + minute * 1200, "climora stats");
        }
        command(3800, "climora cell");
        action(3820, "quit", Minecraft::stop);
    }

    /**
     * Ice of 0.2: sleet just above freezing, then freezing rain glazing the ground at night, then the glaze
     * melting in the sun. In old growth spruce taiga, whose daily mean is just above freezing.
     */
    private static void buildIceScenario() {
        action(1, "small GUI", minecraft -> {
            minecraft.options.guiScale().set(1);
            minecraft.resizeDisplay();
        });
        command(20, "gamerule doDaylightCycle false");
        command(21, "gamerule doMobSpawning false");
        action(25, "fly", minecraft -> {
            minecraft.player.getAbilities().flying = true;
            minecraft.player.onUpdateAbilities();
        });
        command(30, "execute positioned 64 0 64 positioned over motion_blocking run tp @s ~ ~1.6 ~ -45 5");

        // Taiga over all 7x7 simulated cells: a warm neighbour would keep the middle warm.
        int lastFill = fillBiome(45, "minecraft:old_growth_spruce_taiga", 3);
        command(lastFill + 1, "climora resample 3");
        // Early morning under rain: cooled by the rain to just above freezing.
        command(lastFill + 2, "time set 1000");
        command(lastFill + 5, "climora weather rain 3 60000");
        command(lastFill + 10, "tick sprint 12000");
        command(lastFill + 220, "climora temperature");
        screenshot(lastFill + 230, "i01-sleet");

        // Before dawn, the coldest hour, and a warm front overrunning the cold ground air.
        command(lastFill + 240, "time set 20000");
        command(lastFill + 242, "climora weather freezing_rain 3 60000");
        command(lastFill + 245, "tick sprint 12000");
        command(lastFill + 450, "climora temperature");
        command(lastFill + 451, "climora cell");
        onServer(lastFill + 455, "glaze after freezing rain", level -> countAround(level, playerPos(), 24, 12,
                state -> state.is(ClimoraRegistries.GLAZE.get())));
        command(lastFill + 460, "tp @s ~ ~ ~ -45 50");
        screenshot(lastFill + 480, "i02-glaze");

        // A clear, sunny day: the glaze melts.
        command(lastFill + 490, "time set 6000");
        command(lastFill + 491, "climora weather clear 3 60000");
        command(lastFill + 495, "tick sprint 8000");
        command(lastFill + 660, "climora temperature");
        onServer(lastFill + 665, "glaze after a sunny day", level -> countAround(level, playerPos(), 24, 12,
                state -> state.is(ClimoraRegistries.GLAZE.get())));
        action(lastFill + 680, "quit", Minecraft::stop);
    }

    /** Turns the area into tundra, snows on it, freezes a pond, then thaws everything by making it warm again. */
    private static void buildSnowScenario() {
        action(1, "small GUI", minecraft -> {
            minecraft.options.guiScale().set(1);
            minecraft.resizeDisplay();
        });
        command(20, "gamerule doDaylightCycle false");
        command(21, "gamerule doMobSpawning false");
        command(22, "gamerule randomTickSpeed 12");
        command(23, "time set 500");
        action(25, "fly", minecraft -> {
            minecraft.player.getAbilities().flying = true;
            minecraft.player.onUpdateAbilities();
        });
        // Stand in the middle of cell (0, 0) so one biome fills the whole cell.
        command(30, "execute positioned 64 0 64 positioned over motion_blocking run tp @s ~ ~2 ~ -45 5");
        // A shallow pond to watch freeze over.
        command(35, "fill ~2 ~-1 ~2 ~8 ~-1 ~8 minecraft:stone");
        command(36, "fill ~3 ~-1 ~3 ~7 ~-1 ~7 minecraft:water");

        // Tundra over all nine cells around the player: a lone cold cell would be warmed by its neighbours.
        int lastFill = fillBiome(45, "minecraft:snowy_plains");
        command(lastFill + 1, "climora resample 1");
        // Air cools with a two-hour lag, so run the world fast instead of waiting for real minutes.
        command(lastFill + 5, "tick sprint 3600");
        command(lastFill + 140, "climora cell");
        command(lastFill + 141, "climora temperature");
        command(lastFill + 145, "climora weather rain 1 3000");
        command(lastFill + 155, "tick sprint 1200");
        screenshot(lastFill + 360, "01-snowfall");
        command(lastFill + 365, "climora temperature");
        command(lastFill + 370, "tick sprint 2400");
        screenshot(lastFill + 640, "02-snow-on-ground");
        command(lastFill + 645, "execute if block ~5 ~-1 ~5 minecraft:ice run say HARNESS: pond froze");
        command(lastFill + 646, "execute if block ~5 ~-1 ~5 minecraft:water run say HARNESS: pond still liquid");
        command(lastFill + 647, "climora cell");

        // Back to a warm biome: snow and ice must melt even without bright light.
        int lastThaw = fillBiome(lastFill + 660, "minecraft:plains");
        command(lastThaw + 1, "climora resample 1");
        command(lastThaw + 2, "climora weather clear 3 3000");
        command(lastThaw + 10, "tick sprint 6000");
        command(lastThaw + 260, "climora temperature");
        screenshot(lastThaw + 300, "03-thaw");
        command(lastThaw + 305, "execute if block ~5 ~-1 ~5 minecraft:water run say HARNESS: ice melted, pond is liquid again");
        command(lastThaw + 306, "execute if block ~5 ~-1 ~5 minecraft:ice run say HARNESS: pond is still frozen");
        command(lastThaw + 307, "climora cell");
        command(lastThaw + 308, "climora stats");
        action(lastThaw + 330, "quit", Minecraft::stop);
    }

    /**
     * Fills 384x384 blocks around the cell center with one biome. {@code /fillbiome} handles at most
     * 32768 blocks, so the area is covered by 36 commands, one per tick.
     *
     * @return the tick of the last command
     */
    private static int fillBiome(int firstTick, String biome) {
        return fillBiome(firstTick, biome, 1);
    }

    /** Same over the cells within {@code radiusCells} of cell (0, 0). */
    private static int fillBiome(int firstTick, String biome, int radiusCells) {
        int tick = firstTick;
        int from = -128 * radiusCells;
        int to = 128 * (radiusCells + 1);
        for (int x = from; x < to; x += 64) {
            for (int z = from; z < to; z += 64) {
                command(tick++, String.format("fillbiome %d ~-4 %d %d ~3 %d %s", x, z, x + 63, z + 63, biome));
            }
        }
        return tick - 1;
    }

    private static void send(Minecraft minecraft, String command) {
        Climora.LOGGER.info("[Harness] /{}", command);
        minecraft.player.connection.sendCommand(command);
    }

    /** Runs a check on the integrated server thread and logs what it returns. */
    private static void onServer(int tick, String description, Function<ServerLevel, String> probe) {
        steps.add(new Step(tick, description, minecraft -> {
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> Climora.LOGGER.info("[Harness] {}: {}", description, probe.apply(server.overworld())));
            }
        }));
    }

    /** What the client sees: whether it draws our clouds and what it knows about the weather over the camera. */
    private static void reportClientWeather(Minecraft minecraft) {
        ClientWeather weather = ClientWeather.INSTANCE;
        double x = minecraft.player.getX();
        double z = minecraft.player.getZ();
        Climora.LOGGER.info("[Harness] client: active={} ourClouds={} curtains={} cloud={} rain={} fog={} rainLevel={}",
                weather.isActive(), CloudRenderer.replacesVanilla(),
                net.krona.climora.client.PrecipitationRenderer.lastDistantCurtains(),
                String.format(java.util.Locale.ROOT, "%.2f", weather.cloudCover(x, z)),
                String.format(java.util.Locale.ROOT, "%.2f", weather.precipitation(x, z)),
                String.format(java.util.Locale.ROOT, "%.2f", weather.fog(x, z)),
                String.format(java.util.Locale.ROOT, "%.2f", weather.rainLevel(1.0F)));
    }

    private static BlockPos playerPos() {
        return Minecraft.getInstance().player.blockPosition();
    }

    private static ClimateSimulation simulation(ServerLevel level) {
        return ClimateManager.get(level);
    }

    /** A 5x5 floor of planks in the open with fire on every block. */
    private static void buildFireRig(ServerLevel level, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlockAndUpdate(center.offset(dx, 0, dz), Blocks.OAK_PLANKS.defaultBlockState());
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlockAndUpdate(center.offset(dx, 1, dz), Blocks.FIRE.defaultBlockState());
            }
        }
    }

    private static String fireReport(ServerLevel level, BlockPos center) {
        int fire = 0;
        int planks = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockState state = level.getBlockState(center.offset(dx, dy, dz));
                    if (state.is(Blocks.FIRE)) {
                        fire++;
                    } else if (state.is(Blocks.OAK_PLANKS)) {
                        planks++;
                    }
                }
            }
        }
        return fire + " fire blocks burning, " + planks + " of 25 planks left";
    }

    /** Counts blocks matching a test in a box around a position. */
    private static String countAround(ServerLevel level, BlockPos center, int radius, int height, Predicate<BlockState> test) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -height, -radius), center.offset(radius, height, radius))) {
            if (test.test(level.getBlockState(pos))) {
                count++;
            }
        }
        return count + " in " + (radius * 2 + 1) + "x" + (radius * 2 + 1) + " around " + center.toShortString();
    }

    private static void command(int tick, String command) {
        steps.add(new Step(tick, "/" + command, minecraft -> minecraft.player.connection.sendCommand(command)));
    }

    private static void screenshot(int tick, String name) {
        steps.add(new Step(tick, "screenshot " + name, minecraft -> Screenshot.grab(minecraft.gameDirectory,
                "climora-" + name + ".png", minecraft.getMainRenderTarget(),
                message -> Climora.LOGGER.info("[Harness] {}", message.getString()))));
    }

    private static void action(int tick, String description, java.util.function.Consumer<Minecraft> action) {
        steps.add(new Step(tick, description, action));
    }

    private static void createWorld(Minecraft minecraft) {
        Path save = minecraft.gameDirectory.toPath().resolve("saves").resolve(WORLD_NAME);
        deleteRecursively(save);
        GameRules rules = new GameRules();
        LevelSettings settings = new LevelSettings(WORLD_NAME, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                rules, WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD_NAME, settings, new WorldOptions(WORLD_SEED, false, false),
                registries -> preset == null ? WorldPresets.createNormalWorldDimensions(registries)
                        : registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(preset).value().createWorldDimensions(),
                new TitleScreen());
    }

    private static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || nextStep >= steps.size()) {
            return;
        }
        // Keep the window "focused" so the game does not pause when the harness runs in the background.
        minecraft.options.pauseOnLostFocus = false;
        ticksInWorld++;
        while (nextStep < steps.size() && steps.get(nextStep).tick() <= ticksInWorld) {
            Step step = steps.get(nextStep++);
            Climora.LOGGER.info("[Harness] t={} {}", ticksInWorld, step.description());
            try {
                step.action().accept(minecraft);
            } catch (RuntimeException e) {
                Climora.LOGGER.error("[Harness] Step '{}' failed", step.description(), e);
            }
        }
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> files = Files.walk(path)) {
            files.sorted(Comparator.reverseOrder()).forEach(file -> {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    Climora.LOGGER.warn("[Harness] Could not delete {}", file);
                }
            });
        } catch (IOException e) {
            Climora.LOGGER.warn("[Harness] Could not delete {}", path);
        }
    }
}
