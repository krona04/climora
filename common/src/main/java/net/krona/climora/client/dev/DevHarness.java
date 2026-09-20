package net.krona.climora.client.dev;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import net.krona.climora.Climora;
import net.krona.climora.client.ClientWeather;
import net.krona.climora.client.CloudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
            default -> {
                Climora.LOGGER.error("[Harness] Unknown scenario '{}'. Use 'weather' or 'snow'.", scenario);
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
        int tick = firstTick;
        for (int x = -128; x < 256; x += 64) {
            for (int z = -128; z < 256; z += 64) {
                command(tick++, String.format("fillbiome %d ~-4 %d %d ~3 %d %s", x, z, x + 63, z + 63, biome));
            }
        }
        return tick - 1;
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
                WorldPresets::createNormalWorldDimensions, new TitleScreen());
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
