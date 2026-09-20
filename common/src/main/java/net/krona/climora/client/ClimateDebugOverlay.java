package net.krona.climora.client;

import dev.architectury.networking.NetworkManager;
import net.krona.climora.climate.AtmosphereModel;
import net.krona.climora.climate.WeatherOverride;
import net.krona.climora.network.ClimateDebugPayload;
import net.krona.climora.network.DebugSubscriptionPayload;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Climate lines on the left side of the F3 screen.
 * <p>
 * The client subscribes while F3 is open, so the server only sends data that someone is looking at.
 */
public final class ClimateDebugOverlay {
    /** Data older than this is shown as stale (the server sends every 10 ticks). */
    private static final long STALE_AFTER_MILLIS = 2000L;
    private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private static boolean subscribed;
    @Nullable
    private static ClimateDebugPayload latest;
    private static long latestReceivedAt;

    private ClimateDebugOverlay() {
    }

    static void accept(ClimateDebugPayload payload) {
        latest = payload;
        latestReceivedAt = System.currentTimeMillis();
    }

    static void reset() {
        subscribed = false;
        latest = null;
    }

    static void tick(Minecraft minecraft) {
        if (minecraft.isPaused()) {
            // Same reason as in ClientWeather: no packets while paused, so the data is not really stale.
            latestReceivedAt = System.currentTimeMillis();
            return;
        }
        boolean wanted = minecraft.player != null
                && minecraft.getDebugOverlay().showDebugScreen()
                && !minecraft.showOnlyReducedInfo();
        if (wanted == subscribed) {
            return;
        }
        if (minecraft.getConnection() == null || !NetworkManager.canServerReceive(DebugSubscriptionPayload.TYPE)) {
            // Server without Climora: nothing to subscribe to.
            return;
        }
        NetworkManager.sendToServer(new DebugSubscriptionPayload(wanted));
        subscribed = wanted;
        if (!wanted) {
            latest = null;
        }
    }

    static void appendLines(List<String> lines) {
        if (!subscribed) {
            return;
        }
        lines.add("");
        ClimateDebugPayload data = latest;
        if (data == null || System.currentTimeMillis() - latestReceivedAt > STALE_AFTER_MILLIS) {
            lines.add("[Climora] Waiting for climate data...");
            return;
        }
        if (!data.simulated()) {
            lines.add("[Climora] No climate in this dimension");
            return;
        }
        if (Float.isNaN(data.airTemperature())) {
            lines.add("[Climora] No weather cells nearby yet");
            return;
        }

        String surface = Float.isNaN(data.surfaceElevation()) ? "" : format(" (surface Y %.0f)", data.surfaceElevation());
        lines.add(format("[Climora] Air %.1f °C%s, humidity %.0f%%", data.airTemperature(), surface, data.humidity() * 100));
        lines.add(format("Pressure %.0f hPa, clouds %.0f%%, %s", data.pressure(), data.cloudCover() * 100, precipitation(data)));
        float speed = (float) Math.sqrt(data.windX() * data.windX() + data.windZ() * data.windZ());
        lines.add(format("Wind %.1f m/s from %s (Beaufort %.0f)", speed, compass(-data.windX(), -data.windZ()),
                AtmosphereModel.beaufort(speed)));
        if (data.cellExists()) {
            String forced = data.forcedWeather() > 0
                    ? ", forced " + WeatherOverride.Mode.values()[data.forcedWeather() - 1].name().toLowerCase(Locale.ROOT) : "";
            lines.add(format("Cell %d, %d: %.1f -> %.1f °C (biome %.1f °C)%s", data.cellX(), data.cellZ(),
                    data.cellTemperature(), data.targetTemperature(), data.baseTemperature(), forced));
        }
        lines.add(format("Climate sim: %d active cells, %.3f ms/tick", data.activeCells(), data.averageTickMillis()));
    }

    private static String precipitation(ClimateDebugPayload data) {
        AtmosphereModel.PrecipitationType[] types = AtmosphereModel.PrecipitationType.values();
        AtmosphereModel.PrecipitationType type = types[Math.max(0, Math.min(types.length - 1, data.precipitationType()))];
        if (type == AtmosphereModel.PrecipitationType.NONE) {
            return "no precipitation";
        }
        String storm = data.storm() > 0.15F ? format(", storm %.0f%%", data.storm() * 100) : "";
        return format("%s %.0f%%%s", type.name().toLowerCase(Locale.ROOT), data.precipitation() * 100, storm);
    }

    private static String compass(float x, float z) {
        if (x * x + z * z < 1.0E-4F) {
            return "-";
        }
        double degrees = Math.toDegrees(Math.atan2(x, -z));
        return COMPASS[Math.floorMod(Math.round(degrees / 45.0), 8)];
    }

    private static String format(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }
}
