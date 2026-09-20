package net.krona.climora.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.architectury.platform.Platform;
import net.krona.climora.Climora;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * JSON configs in the {@code config} folder. Missing files and fields are filled with defaults and
 * written back, so every option is always visible in the file.
 * <ul>
 *     <li>{@code climora.json} — simulation and gameplay, used by the server (and singleplayer)</li>
 *     <li>{@code climora-client.json} — rendering, used by the client only</li>
 * </ul>
 */
public final class ClimoraConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Server server = new Server();
    private static Client client = new Client();

    private ClimoraConfig() {
    }

    public static Server server() {
        return server;
    }

    public static Client client() {
        return client;
    }

    public static void loadServer() {
        server = load("climora.json", "tempestra.json", Server.class, Server::new);
        server.validate();
        save("climora.json", server);
    }

    public static void loadClient() {
        client = load("climora-client.json", "tempestra-client.json", Client.class, Client::new);
        client.validate();
        save("climora-client.json", client);
    }

    private static <T> T load(String fileName, String legacyFileName, Class<T> type, Supplier<T> defaults) {
        Path path = Platform.getConfigFolder().resolve(fileName);
        if (!Files.exists(path)) {
            // The mod was called Tempestra until 0.1.1: keep the settings its users had.
            Path legacy = Platform.getConfigFolder().resolve(legacyFileName);
            if (!Files.exists(legacy)) {
                return defaults.get();
            }
            Climora.LOGGER.info("Taking settings over from {}", legacy);
            path = legacy;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            T value = GSON.fromJson(reader, type);
            return value != null ? value : defaults.get();
        } catch (IOException | JsonParseException e) {
            Climora.LOGGER.error("Could not read config {}, using defaults: {}", path, e.getMessage());
            return defaults.get();
        }
    }

    private static void save(String fileName, Object value) {
        Path path = Platform.getConfigFolder().resolve(fileName);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(value, writer);
            }
        } catch (IOException e) {
            Climora.LOGGER.error("Could not write config {}: {}", path, e.getMessage());
        }
    }

    /** Simulation and gameplay options. Reload in game with {@code /climora reload}. */
    public static final class Server {
        public String _about = "Climora server config. Reload with /climora reload. See docs/CONFIG.md for every option.";

        /** Replace the global vanilla rain with local weather. When false, the climate is still simulated but vanilla weather is used. */
        public boolean localWeather = true;
        /** Cells (128 blocks each) simulated around every player. 3 means a 7x7 area, about 900x900 blocks. */
        public int activeRadiusCells = 3;
        /** Time the simulation may use per tick, milliseconds. */
        public double tickBudgetMillis = 1.0;
        /** New cells sampled from the terrain per tick. Sampling asks the world generator for heights. */
        public int maxCellSamplesPerTick = 1;

        /** Added to the humidity the air tends to. Positive makes the world wetter, negative drier. -0.3..0.3 */
        public double humidityBias = 0.0;
        /** Snow layers pile up while it snows. */
        public boolean snowAccumulation = true;
        /** Snow and ice melt where the air is above freezing, even without sunlight. */
        public boolean snowMelting = true;
        /** Still water freezes where the air is below freezing. */
        public boolean waterFreezing = true;
        /** Thunderstorms with local lightning. */
        public boolean thunderstorms = true;
        /** Multiplier for lightning strikes in thunderstorms. 0 disables lightning, storms stay. */
        public double lightningRate = 1.0;
        /** Make the vanilla /weather command start local weather around the player instead of doing nothing. */
        public boolean redirectWeatherCommand = true;

        void validate() {
            activeRadiusCells = clamp(activeRadiusCells, 1, 8);
            tickBudgetMillis = clamp(tickBudgetMillis, 0.1, 20.0);
            maxCellSamplesPerTick = clamp(maxCellSamplesPerTick, 1, 16);
            humidityBias = clamp(humidityBias, -0.3, 0.3);
            lightningRate = clamp(lightningRate, 0.0, 10.0);
        }
    }

    /** Rendering options. Read when the game starts. */
    public static final class Client {
        public String _about = "Climora client config. Restart the game to apply. See docs/CONFIG.md for every option.";

        /** Draw rain and snow only where it actually rains. When false, vanilla rain rendering is used. */
        public boolean localPrecipitation = true;
        /** Rain slants and snow drifts with the wind. */
        public boolean windBlownPrecipitation = true;
        /** Replace vanilla clouds with clouds that follow the simulated cloud cover. */
        public boolean dynamicClouds = true;
        /** How far dynamic clouds are drawn, blocks. 0 follows the render distance of the game. */
        public int cloudRenderDistance = 0;

        void validate() {
            if (cloudRenderDistance != 0) {
                cloudRenderDistance = clamp(cloudRenderDistance, 96, 1024);
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
    }
}
