package net.krona.climora.climate;

/**
 * Weather forced by a command in an area of cells for a limited time.
 *
 * @param untilTick game time when the override ends
 */
public record WeatherOverride(Mode mode, int centerCellX, int centerCellZ, int radiusCells, long untilTick) {
    /** Lowest temperature of the air aloft when a mode forces it warm, °C: well above melting snow. */
    public static final float WARM_ALOFT = 5.0F;
    /** Relaxation time of clouds, precipitation and storms inside an override, ticks. Commands should act fast. */
    public static final float RESPONSE_TIME_TICKS = 100.0F;

    /**
     * What each mode forces. Humidity is relative to saturation at the daily mean temperature.
     */
    public enum Mode {
        CLEAR(0.0F, 0.0F, 0.0F, 0.0F, 0.5F, Float.NaN, 1.0F),
        CLOUDY(0.3F, 0.7F, 0.0F, 0.0F, 0.85F, Float.NaN, 1.0F),
        /**
         * Clear, still and humid. Nothing forces the fog itself: it forms by night as the ground air cools
         * below its dew point, and burns off in the morning.
         */
        FOG(0.0F, 0.0F, 0.0F, 0.0F, 1.05F, Float.NaN, 0.2F),
        DRIZZLE(0.6F, 1.0F, 0.15F, 0.0F, 1.0F, Float.NaN, 1.0F),
        RAIN(1.0F, 1.0F, 0.75F, 0.0F, 1.02F, Float.NaN, 1.0F),
        DOWNPOUR(1.0F, 1.0F, 1.0F, 0.0F, 1.05F, Float.NaN, 1.0F),
        STORM(1.0F, 1.0F, 1.0F, 1.0F, 1.02F, Float.NaN, 1.0F),
        /**
         * A warm front over cold ground: air aloft is held above +5 °C and several degrees warmer than the
         * ground, so the rain freezes on contact wherever the ground air is below zero.
         */
        FREEZING_RAIN(1.0F, 1.0F, 0.6F, 0.0F, 1.02F, 6.0F, 1.0F);

        public final float lift;
        public final float cloudCover;
        public final float precipitation;
        public final float storm;
        public final float humidity;
        /**
         * Air aloft is held at least this much warmer than the ground air and at least
         * {@link #WARM_ALOFT} °C, or NaN to leave it to the simulation.
         */
        public final float columnWarming;
        /** Share of the simulated wind that is left. */
        public final float windScale;

        Mode(float lift, float cloudCover, float precipitation, float storm, float humidity, float columnWarming,
             float windScale) {
            this.lift = lift;
            this.cloudCover = cloudCover;
            this.precipitation = precipitation;
            this.storm = storm;
            this.humidity = humidity;
            this.columnWarming = columnWarming;
            this.windScale = windScale;
        }
    }

    public boolean covers(int cellX, int cellZ) {
        return Math.max(Math.abs(cellX - centerCellX), Math.abs(cellZ - centerCellZ)) <= radiusCells;
    }

    public boolean isActive(long gameTime) {
        return gameTime < untilTick;
    }
}
