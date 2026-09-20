package net.krona.climora.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.krona.climora.climate.AtmosphereModel;
import net.krona.climora.climate.CellPos;
import net.krona.climora.climate.ClimateModel;
import net.krona.climora.climate.SimulationConstants;
import net.krona.climora.network.LocalWeatherPayload;
import net.krona.climora.weather.LocalWeather;
import net.krona.climora.weather.SkyWeather;
import net.krona.climora.weather.WeatherView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Local weather on the client, built from {@link LocalWeatherPayload}s.
 * <p>
 * Values ease towards each new payload over about a second, so weather never jumps, and are
 * interpolated between cell centers like on the server.
 */
public final class ClientWeather implements WeatherView, SkyWeather {
    public static final ClientWeather INSTANCE = new ClientWeather();

    /** Share of the remaining difference closed per tick: ~1 s to settle after a payload. */
    private static final float EASING_PER_TICK = 0.1F;
    /** Without payloads for this long, fall back to vanilla weather. */
    private static final long TIMEOUT_MILLIS = 5000L;
    /** Precipitation at which the sky is as dark as in vanilla rain. */
    private static final float FULL_RAIN_LEVEL_PRECIPITATION = 0.6F;

    private static final int TEMPERATURE = 0;
    private static final int ELEVATION = 1;
    private static final int HUMIDITY = 2;
    private static final int CLOUD = 3;
    private static final int PRECIPITATION = 4;
    private static final int STORM = 5;
    private static final int WIND_X = 6;
    private static final int WIND_Z = 7;
    private static final int FIELD_COUNT = 8;

    private final Long2ObjectOpenHashMap<float[][]> cells = new Long2ObjectOpenHashMap<>();
    @Nullable
    private ClientLevel level;
    private long lastPayloadMillis;
    private boolean active;

    private int originX;
    private int originZ;
    private int size;
    private double cloudOffsetX;
    private double cloudOffsetZ;
    private float driftX;
    private float driftZ;
    private long payloadGameTime;

    private float rainLevel;
    private float previousRainLevel;
    private float thunderLevel;
    private float previousThunderLevel;

    private ClientWeather() {
    }

    public boolean isActive() {
        return active;
    }

    void accept(LocalWeatherPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!payload.isActive() || minecraft.level == null) {
            reset();
            return;
        }
        if (minecraft.level != level) {
            reset();
            level = minecraft.level;
        }

        LongOpenHashSet received = new LongOpenHashSet();
        for (int dz = 0; dz < payload.size(); dz++) {
            for (int dx = 0; dx < payload.size(); dx++) {
                LocalWeatherPayload.Cell cell = payload.cells()[dz * payload.size() + dx];
                if (!cell.present()) {
                    continue;
                }
                long key = CellPos.key(payload.originX() + dx, payload.originZ() + dz);
                received.add(key);
                float[] target = {cell.temperature(), cell.elevation(), cell.humidity(), cell.cloudCover(),
                        cell.precipitation(), cell.storm(), cell.windX(), cell.windZ()};
                float[][] values = cells.get(key);
                if (values == null) {
                    cells.put(key, new float[][]{target.clone(), target});
                } else {
                    values[1] = target;
                }
            }
        }
        cells.keySet().removeIf(key -> !received.contains(key));

        originX = payload.originX();
        originZ = payload.originZ();
        size = payload.size();
        cloudOffsetX = payload.cloudOffsetX();
        cloudOffsetZ = payload.cloudOffsetZ();
        driftX = payload.driftX();
        driftZ = payload.driftZ();
        payloadGameTime = payload.gameTime();
        lastPayloadMillis = System.currentTimeMillis();
        if (!active) {
            active = true;
            LocalWeather.setClient(this, this);
        }
    }

    void reset() {
        cells.clear();
        level = null;
        size = 0;
        rainLevel = previousRainLevel = thunderLevel = previousThunderLevel = 0.0F;
        if (active) {
            active = false;
            LocalWeather.setClient(null, null);
        }
    }

    void tick(Minecraft minecraft) {
        if (!active) {
            return;
        }
        if (minecraft.isPaused()) {
            // A paused singleplayer game freezes the integrated server, so no payloads arrive while the
            // wall clock keeps running. Without this the link looks dead after TIMEOUT_MILLIS and the sky
            // falls back to vanilla clouds until the player unpauses. Keeping the stamp fresh also stops
            // the timeout from firing on the first tick after unpausing.
            lastPayloadMillis = System.currentTimeMillis();
            return;
        }
        if (minecraft.level != level || System.currentTimeMillis() - lastPayloadMillis > TIMEOUT_MILLIS) {
            reset();
            return;
        }

        for (float[][] values : cells.values()) {
            float[] current = values[0];
            float[] target = values[1];
            for (int i = 0; i < FIELD_COUNT; i++) {
                current[i] += (target[i] - current[i]) * EASING_PER_TICK;
            }
        }

        previousRainLevel = rainLevel;
        previousThunderLevel = thunderLevel;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 position = camera.isInitialized() ? camera.getPosition()
                : minecraft.player != null ? minecraft.player.position() : Vec3.ZERO;
        float precipitation = precipitation(position.x, position.z);
        float targetRain = precipitation < LocalWeather.PRECIPITATION_THRESHOLD
                ? 0.0F : Mth.clamp(precipitation / FULL_RAIN_LEVEL_PRECIPITATION, 0.0F, 1.0F);
        rainLevel += (targetRain - rainLevel) * EASING_PER_TICK;
        thunderLevel += (storm(position.x, position.z) * rainLevel - thunderLevel) * EASING_PER_TICK;
    }

    @Override
    public float rainLevel(float partialTick) {
        return Mth.lerp(partialTick, previousRainLevel, rainLevel);
    }

    @Override
    public float thunderLevel(float partialTick) {
        return Mth.lerp(partialTick, previousThunderLevel, thunderLevel);
    }

    @Override
    public float precipitation(double x, double z) {
        return value(x, z, PRECIPITATION, 0.0F);
    }

    @Override
    public float storm(double x, double z) {
        return value(x, z, STORM, 0.0F);
    }

    @Override
    public float airTemperature(double x, double y, double z) {
        float surface = value(x, z, TEMPERATURE, Float.NaN);
        if (Float.isNaN(surface)) {
            return Float.NaN;
        }
        float elevation = value(x, z, ELEVATION, Float.NaN);
        return Float.isNaN(elevation) ? surface : surface + ClimateModel.altitudeOffset(elevation, (float) y);
    }

    public float cloudCover(double x, double z) {
        // Same coupling the server applies per cell and when interpolating: where it rains, the sky is shut.
        return Math.max(value(x, z, CLOUD, 0.0F),
                AtmosphereModel.overcastFor(value(x, z, PRECIPITATION, 0.0F)));
    }

    public float humidity(double x, double z) {
        return value(x, z, HUMIDITY, 0.0F);
    }

    public float windX(double x, double z) {
        return value(x, z, WIND_X, 0.0F);
    }

    public float windZ(double x, double z) {
        return value(x, z, WIND_Z, 0.0F);
    }

    /** Like {@link #cloudCover} but positions outside the received area use the nearest edge. */
    public float cloudCoverClamped(double x, double z) {
        if (size == 0) {
            return 0.0F;
        }
        double half = SimulationConstants.CELL_SIZE_BLOCKS / 2.0;
        double minX = CellPos.minBlock(originX) + half;
        double maxX = CellPos.minBlock(originX + size - 1) + half;
        double minZ = CellPos.minBlock(originZ) + half;
        double maxZ = CellPos.minBlock(originZ + size - 1) + half;
        return cloudCover(Mth.clamp(x, minX, maxX), Mth.clamp(z, minZ, maxZ));
    }

    /** Same as {@link #cloudCoverClamped} for precipitation, used to darken rain clouds. */
    public float precipitationClamped(double x, double z) {
        if (size == 0) {
            return 0.0F;
        }
        double half = SimulationConstants.CELL_SIZE_BLOCKS / 2.0;
        return precipitation(
                Mth.clamp(x, CellPos.minBlock(originX) + half, CellPos.minBlock(originX + size - 1) + half),
                Mth.clamp(z, CellPos.minBlock(originZ) + half, CellPos.minBlock(originZ + size - 1) + half));
    }

    /** Same as {@link #cloudCoverClamped} for storms, used to build tall storm clouds. */
    public float stormClamped(double x, double z) {
        if (size == 0) {
            return 0.0F;
        }
        double half = SimulationConstants.CELL_SIZE_BLOCKS / 2.0;
        return storm(
                Mth.clamp(x, CellPos.minBlock(originX) + half, CellPos.minBlock(originX + size - 1) + half),
                Mth.clamp(z, CellPos.minBlock(originZ) + half, CellPos.minBlock(originZ + size - 1) + half));
    }

    /** How far weather systems (and clouds) have drifted, extrapolated to the given client game time. */
    public double cloudOffsetX(double gameTime) {
        return cloudOffsetX + driftX * (gameTime - payloadGameTime);
    }

    public double cloudOffsetZ(double gameTime) {
        return cloudOffsetZ + driftZ * (gameTime - payloadGameTime);
    }

    private float value(double x, double z, int field, float missing) {
        double cellSize = SimulationConstants.CELL_SIZE_BLOCKS;
        double fx = (x - cellSize / 2.0) / cellSize;
        double fz = (z - cellSize / 2.0) / cellSize;
        int x0 = Mth.floor(fx);
        int z0 = Mth.floor(fz);
        double tx = fx - x0;
        double tz = fz - z0;

        double sum = 0.0;
        double weightSum = 0.0;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                float[][] values = cells.get(CellPos.key(x0 + dx, z0 + dz));
                if (values == null) {
                    continue;
                }
                double weight = Math.max((dx == 0 ? 1.0 - tx : tx) * (dz == 0 ? 1.0 - tz : tz), 1.0E-6);
                sum += values[0][field] * weight;
                weightSum += weight;
            }
        }
        return weightSum > 0.0 ? (float) (sum / weightSum) : missing;
    }
}
