package net.krona.climora.client.compat;

import net.krona.climora.client.ClientWeather;
import net.krona.climora.climate.AtmosphereModel;
import net.krona.climora.weather.LocalWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import pigcart.particlerain.ParticleSpawner;
import pigcart.particlerain.config.ParticleData;

import java.util.List;

/**
 * Local weather for Particle Rain's spawner, called from {@code mixin.compat}.
 * <p>
 * Particle Rain decides from the global rain level whether weather particles spawn at all, and from the biome
 * whether a spot gets rain or snow. Here both come from Climora: particles spawn where the local weather has
 * precipitation, as rain or snow by the air temperature there (so it snows on a cold plain and rains on a warm
 * taiga), and as densely as it falls: a drizzle is sparse, a downpour dense.
 * <p>
 * Names Particle Rain's classes: only reached through mixins that apply when it is loaded.
 */
public final class ParticleRainWeather {
    /** Farthest a weather particle spawns from the camera: block effects reach 32 blocks, sky effects 16. */
    private static final int REACH = 32;
    /** Share of spawn attempts kept in the lightest drizzle; steady rain keeps them all. */
    private static final float MIN_DENSITY = 0.25F;
    /** Precipitation at which every spawn attempt is kept. */
    private static final float FULL_DENSITY = 0.35F;

    private static final RandomSource RANDOM = RandomSource.create();
    private static long nearTick = Long.MIN_VALUE;
    private static boolean near;
    private static boolean rainingHere;

    private ParticleRainWeather() {
    }

    private static boolean active() {
        return ClientWeather.INSTANCE.isActive();
    }

    /**
     * Every client tick, before Particle Rain spawns anything. Its "after the rain" particles (drips and the
     * like) start when the global rain stops, which never happens with local weather; they start when the
     * local rain over the camera stops instead.
     */
    public static void tick(ClientLevel level) {
        if (!active()) {
            return;
        }
        boolean raining = level.getRainLevel(1.0F) > 0.0F;
        if (raining != rainingHere) {
            rainingHere = raining;
            ParticleSpawner.onWeatherChange(raining);
        }
    }

    /**
     * Whether a kind of weather particle is current. Particle Rain asks the rain level at the camera, which
     * would leave a shower ten blocks away without a single drop; with local weather, rain particles are
     * current when it rains anywhere within their reach, and {@link #precipitationAt} picks the spots.
     */
    public static boolean isCurrent(ParticleData.Weather weather, ClientLevel level, boolean original) {
        if (!active()) {
            return original;
        }
        return switch (weather) {
            case DURING_WEATHER -> precipitationNear(level);
            case ONLY_DURING_NORMAL_WEATHER -> precipitationNear(level) && !(level.getThunderLevel(1.0F) > 0.0F);
            default -> original;
        };
    }

    /**
     * What falls at a spot Particle Rain wants to spawn at. {@code null} matches no particle: nothing spawns
     * there this time. Dry land stays dry, except in biomes where it never rains, which keep Particle Rain's
     * dust while there is weather around.
     */
    @Nullable
    public static Biome.Precipitation precipitationAt(BlockPos pos, Biome.Precipitation original) {
        if (!active()) {
            return original;
        }
        ClientWeather weather = ClientWeather.INSTANCE;
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        float intensity = weather.precipitation(x, z);
        if (intensity < LocalWeather.PRECIPITATION_THRESHOLD) {
            return original == Biome.Precipitation.NONE ? Biome.Precipitation.NONE : null;
        }
        if (RANDOM.nextFloat() > density(intensity)) {
            return null;
        }
        return switch (LocalWeather.precipitationType(weather, x, pos.getY(), z)) {
            case SNOW -> Biome.Precipitation.SNOW;
            case SLEET -> RANDOM.nextBoolean() ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
            default -> Biome.Precipitation.RAIN;
        };
    }

    /**
     * What a falling particle finds where it is now. Particle Rain removes particles that drift into a biome
     * with other precipitation; with local weather a snowflake over a plains biome is right where it is, and
     * in sleet both rain and snow belong.
     */
    public static Biome.Precipitation underParticle(BlockPos pos, List<Biome.Precipitation> accepted, Biome.Precipitation original) {
        if (!active()) {
            return original;
        }
        return switch (LocalWeather.precipitationAt(ClientWeather.INSTANCE, pos)) {
            case NONE -> Biome.Precipitation.NONE;
            case SNOW -> Biome.Precipitation.SNOW;
            case SLEET -> accepted.contains(Biome.Precipitation.SNOW) ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
            default -> Biome.Precipitation.RAIN;
        };
    }

    /** Is anything falling within reach of the camera? Asked for every spawn attempt, so once per tick. */
    private static boolean precipitationNear(ClientLevel level) {
        long tick = level.getGameTime();
        if (tick != nearTick) {
            nearTick = tick;
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            near = false;
            for (int dx = -REACH; dx <= REACH && !near; dx += REACH) {
                for (int dz = -REACH; dz <= REACH && !near; dz += REACH) {
                    near = ClientWeather.INSTANCE.precipitation(camera.x + dx, camera.z + dz) >= LocalWeather.PRECIPITATION_THRESHOLD;
                }
            }
        }
        return near;
    }

    private static float density(float intensity) {
        return MIN_DENSITY + (1.0F - MIN_DENSITY)
                * AtmosphereModel.smoothstep(LocalWeather.PRECIPITATION_THRESHOLD, FULL_DENSITY, intensity);
    }
}
