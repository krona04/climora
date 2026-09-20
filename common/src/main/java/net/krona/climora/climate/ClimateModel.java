package net.krona.climora.climate;

/**
 * Pure climate formulas with no Minecraft dependencies. See docs/DESIGN.md for the reasoning behind each value.
 */
public final class ClimateModel {
    /** °C = OFFSET + SCALE * vanilla biome temperature. Placeholder until climate profiles in 0.1.0. */
    public static final float BIOME_TEMPERATURE_OFFSET = -2.0F;
    public static final float BIOME_TEMPERATURE_SCALE = 20.0F;

    /** Half of the day/night temperature swing for fully humid and fully dry cells, °C. */
    public static final float HUMID_DIURNAL_AMPLITUDE = 4.0F;
    public static final float DRY_DIURNAL_AMPLITUDE = 12.0F;

    public static final int TICKS_PER_DAY = 24000;
    /** Day time of the warmest moment, about 15:00 (day time 0 is 06:00). */
    public static final int WARMEST_DAY_TIME = 9000;

    /**
     * Cooling per block of height, °C. Real air cools by 0.0065 °C/m, but Minecraft terrain is
     * about five times flatter than real mountains, so the game-scale rate is 0.03 °C/block.
     */
    public static final float LAPSE_RATE_PER_BLOCK = 0.03F;

    private ClimateModel() {
    }

    public static float biomeToCelsius(float biomeTemperature) {
        return BIOME_TEMPERATURE_OFFSET + BIOME_TEMPERATURE_SCALE * biomeTemperature;
    }

    public static float diurnalAmplitude(float humidity) {
        float dryness = 1.0F - clamp01(humidity);
        return HUMID_DIURNAL_AMPLITUDE + (DRY_DIURNAL_AMPLITUDE - HUMID_DIURNAL_AMPLITUDE) * dryness;
    }

    /** Temperature difference from the daily mean at this time of day, °C. */
    public static float diurnalOffset(float amplitude, long dayTime) {
        double phase = Math.floorMod(dayTime - WARMEST_DAY_TIME, TICKS_PER_DAY) / (double) TICKS_PER_DAY;
        return (float) (amplitude * Math.cos(phase * 2.0 * Math.PI));
    }

    /** Temperature change from {@code fromHeight} to {@code toHeight}: negative when going up. */
    public static float altitudeOffset(float fromHeight, float toHeight) {
        return -LAPSE_RATE_PER_BLOCK * (toHeight - fromHeight);
    }

    /**
     * Temperature a cell tends to right now.
     *
     * @param baseTemperature daily mean temperature of the cell at its own height, °C
     * @param amplitude       half of the day/night swing of the cell, °C
     */
    public static float targetTemperature(float baseTemperature, float amplitude, long dayTime) {
        return baseTemperature + diurnalOffset(amplitude, dayTime);
    }

    /**
     * Exponential relaxation towards the target. Exact for any step length, so cells that were
     * inactive for a long time catch up in a single step.
     */
    public static float relax(float current, float target, long elapsedTicks, float timeConstantTicks) {
        if (elapsedTicks <= 0) {
            return current;
        }
        double blend = 1.0 - Math.exp(-elapsedTicks / (double) timeConstantTicks);
        return (float) (current + (target - current) * blend);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
