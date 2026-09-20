package net.krona.climora.climate;

/**
 * Pure formulas for pressure, moisture, clouds, precipitation, wind and storms.
 * See docs/DESIGN.md §6 for how they fit together.
 */
public final class AtmosphereModel {
    // --- Pressure -----------------------------------------------------------------------------

    /** Local pressure drop per °C a cell is warmer than its neighbours (warm air rises), hPa/°C. */
    public static final float THERMAL_PRESSURE_PER_DEGREE = 1.2F;
    /** At or above this pressure air sinks and no precipitation forms, hPa. */
    public static final float LIFT_START_PRESSURE = 1016.0F;
    /** At or below this pressure the lift is at its maximum, hPa. */
    public static final float LIFT_FULL_PRESSURE = 998.0F;

    // --- Moisture -----------------------------------------------------------------------------

    /** Relative humidity the air tends to over the driest land. */
    public static final float DRY_EQUILIBRIUM_HUMIDITY = 0.35F;
    /** Added by biome downfall (0..1). */
    public static final float DOWNFALL_HUMIDITY = 0.6F;
    /** Added over open water (ocean and river biomes). */
    public static final float WATER_HUMIDITY = 0.15F;
    /** Added in rising air: low pressure draws moisture together. */
    public static final float LIFT_HUMIDITY = 0.35F;
    /** Time for evaporation to close ~63% of the gap to the equilibrium moisture, ticks. */
    public static final float EVAPORATION_TIME_TICKS = 6000.0F;
    /** Time for full-intensity precipitation to remove ~63% of the moisture, ticks. */
    public static final float PRECIPITATION_DRYING_TICKS = 20000.0F;

    // --- Clouds and precipitation -------------------------------------------------------------

    public static final float CLOUD_TIME_TICKS = 1200.0F;
    public static final float PRECIPITATION_TIME_TICKS = 600.0F;
    public static final float STORM_TIME_TICKS = 900.0F;

    /** Clouds cut the day/night temperature swing by up to this share. */
    public static final float CLOUD_DIURNAL_DAMPING = 0.6F;
    /** Heavy precipitation cools the air by this much, °C. */
    public static final float PRECIPITATION_COOLING = 3.0F;

    /** Precipitation falls as snow at or below this temperature, and as rain above {@link #RAIN_TEMPERATURE}. */
    public static final float SNOW_TEMPERATURE = 0.0F;
    public static final float RAIN_TEMPERATURE = 2.0F;

    // --- Wind ---------------------------------------------------------------------------------

    /** Weather systems drift at the steering wind. Blocks per tick of drift for 1 m/s of wind. */
    public static final double ADVECTION_BLOCKS_PER_TICK_PER_MPS = 0.02;
    /** Wind from the pressure gradient, m/s per hPa/block. */
    public static final double GRADIENT_WIND_FACTOR = 300.0;
    /** Share of the steering flow felt near the ground. */
    public static final double SURFACE_STEERING_SHARE = 0.8;
    public static final double MAX_WIND_SPEED = 32.0;

    public enum PrecipitationType {
        NONE, RAIN, SLEET, SNOW
    }

    private AtmosphereModel() {
    }

    /** Saturation vapour pressure over water (Magnus formula), hPa. */
    public static double saturationVaporPressure(double temperature) {
        return 6.112 * Math.exp(17.67 * temperature / (temperature + 243.5));
    }

    /** Moisture of saturated air, g of water per kg of dry air. */
    public static double saturationMixingRatio(double temperature, double pressure) {
        double vapor = saturationVaporPressure(temperature);
        return 622.0 * vapor / Math.max(pressure - vapor, 1.0);
    }

    public static float relativeHumidity(float moisture, float temperature, float pressure) {
        return (float) (moisture / saturationMixingRatio(temperature, pressure));
    }

    /** How strongly air rises at this pressure: 0 in highs, 1 in deep lows. */
    public static float lift(float pressure) {
        return smoothstep(LIFT_START_PRESSURE, LIFT_FULL_PRESSURE, pressure);
    }

    /** Relative humidity the air tends to by evaporation and convergence. */
    public static float equilibriumHumidity(float baseHumidity, float waterFraction, float lift) {
        float humidity = DRY_EQUILIBRIUM_HUMIDITY + DOWNFALL_HUMIDITY * clamp01(baseHumidity)
                + WATER_HUMIDITY * clamp01(waterFraction) + LIFT_HUMIDITY * clamp01(lift);
        return Math.min(humidity, 1.05F);
    }

    /**
     * Cloud cover the cell tends to.
     *
     * @param columnHumidity relative humidity at the daily mean temperature: clouds form from moisture
     *                       lifted aloft, not from the nightly cooling near the ground
     */
    public static float cloudTarget(float columnHumidity, float lift) {
        return smoothstep(0.55F, 0.92F, columnHumidity) * (0.25F + 0.75F * clamp01(lift));
    }

    /** Precipitation intensity the cell tends to, 0..1. Needs both moist air and rising air. */
    public static float precipitationTarget(float columnHumidity, float lift) {
        return smoothstep(0.8F, 1.0F, columnHumidity) * smoothstep(0.25F, 0.7F, lift);
    }

    /**
     * Precipitation at which the sky above is solidly overcast. Equal to the intensity at which rain
     * starts being drawn ({@code LocalWeather.PRECIPITATION_THRESHOLD}), so the first drop and the closed
     * cloud deck arrive together; {@code AtmosphereModelTest} keeps the two values in step.
     */
    public static final float OVERCAST_PRECIPITATION = 0.08F;

    /**
     * Cloud cover that falling precipitation implies on its own. Rain has to fall out of something, so a
     * cell never rains through a sky the cloud formula left half empty: cover is raised to at least this.
     */
    public static float overcastFor(float precipitation) {
        return clamp01(precipitation / OVERCAST_PRECIPITATION);
    }

    /**
     * Thunderstorm intensity the cell tends to, 0..1. Storms need warm, very humid air that is forced
     * upwards, either by a low or by afternoon heating.
     *
     * @param heating 0..1, how much the sun is heating the ground right now
     */
    public static float stormTarget(float temperature, float columnHumidity, float lift, float precipitation, float heating) {
        float instability = smoothstep(12.0F, 26.0F, temperature) * smoothstep(0.85F, 1.0F, columnHumidity);
        float trigger = Math.max(clamp01(lift), 0.8F * clamp01(heating));
        return instability * smoothstep(0.35F, 0.8F, precipitation) * smoothstep(0.3F, 0.9F, trigger);
    }

    /**
     * Wind grows with height above the ground: +50% at 100 blocks, no more above that.
     *
     * @param heightAboveSurface blocks; NaN is treated as ground level
     */
    public static float windAtHeight(float groundSpeed, float heightAboveSurface) {
        float height = Float.isNaN(heightAboveSurface) ? 0.0F : clamp01(heightAboveSurface / 100.0F);
        return groundSpeed * (1.0F + 0.5F * height);
    }

    /** Beaufort number for a wind speed in m/s, 0..12. */
    public static float beaufort(float speed) {
        return Math.min(12.0F, (float) Math.pow(Math.max(speed, 0.0F) / 0.836, 2.0 / 3.0));
    }

    public static PrecipitationType precipitationType(float temperature) {
        if (temperature <= SNOW_TEMPERATURE) {
            return PrecipitationType.SNOW;
        }
        return temperature <= RAIN_TEMPERATURE ? PrecipitationType.SLEET : PrecipitationType.RAIN;
    }

    public static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0F - 2.0F * t);
    }

    public static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
