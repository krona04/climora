package net.krona.climora.worldgen;

/**
 * Climate belts of the "Climate belts" world type, as the climate parameters the vanilla biome placement
 * reads. The equator runs along z = 0; the poles are {@code halfWidth} blocks north and south of it.
 * <p>
 * Vanilla places biomes by temperature bands: below -0.45 frozen (snowy plains, ice, frozen ocean), up to
 * -0.15 cold (taiga), up to 0.2 temperate (forests, plains), up to 0.55 warm (savanna, jungle), above that
 * hot (desert, badlands). Humidity picks within a band: in the warm band the dry end is savanna and the wet
 * end jungle. The belts follow the Earth: wet tropics at the equator, the dry subtropics a quarter of the way
 * to the pole, temperate land in the middle, boreal forest, tundra, and ice past the poles.
 */
public final class Latitude {
    // Latitude 0 (equator) .. 1 (pole), and the parameter the belt has there.
    private static final float[] POINTS = {0.0F, 0.12F, 0.25F, 0.35F, 0.45F, 0.6F, 0.72F, 0.85F, 1.0F};
    private static final float[] TEMPERATURE = {0.42F, 0.45F, 0.78F, 0.62F, 0.1F, -0.05F, -0.3F, -0.62F, -0.95F};
    private static final float[] HUMIDITY = {0.55F, 0.35F, -0.5F, -0.25F, 0.15F, 0.1F, 0.2F, 0.0F, -0.2F};

    private Latitude() {
    }

    /** 0 at the equator, 1 at a pole and beyond: past the poles it is ice all the way. */
    public static float of(double z, double halfWidth) {
        return (float) Math.min(1.0, Math.abs(z) / halfWidth);
    }

    public static float temperature(float latitude) {
        return interpolate(TEMPERATURE, latitude);
    }

    public static float humidity(float latitude) {
        return interpolate(HUMIDITY, latitude);
    }

    public static float minTemperature() {
        return min(TEMPERATURE);
    }

    public static float maxTemperature() {
        return max(TEMPERATURE);
    }

    public static float minHumidity() {
        return min(HUMIDITY);
    }

    public static float maxHumidity() {
        return max(HUMIDITY);
    }

    private static float interpolate(float[] values, float latitude) {
        if (latitude <= POINTS[0]) {
            return values[0];
        }
        for (int i = 1; i < POINTS.length; i++) {
            if (latitude <= POINTS[i]) {
                float t = (latitude - POINTS[i - 1]) / (POINTS[i] - POINTS[i - 1]);
                // Smooth between the points, so a belt does not end in a kink.
                t = t * t * (3.0F - 2.0F * t);
                return values[i - 1] + (values[i] - values[i - 1]) * t;
            }
        }
        return values[values.length - 1];
    }

    private static float min(float[] values) {
        float min = Float.MAX_VALUE;
        for (float value : values) {
            min = Math.min(min, value);
        }
        return min;
    }

    private static float max(float[] values) {
        float max = -Float.MAX_VALUE;
        for (float value : values) {
            max = Math.max(max, value);
        }
        return max;
    }
}
