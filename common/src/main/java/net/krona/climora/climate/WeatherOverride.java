package net.krona.climora.climate;

/**
 * Weather forced by a command in an area of cells for a limited time.
 *
 * @param untilTick game time when the override ends
 */
public record WeatherOverride(Mode mode, int centerCellX, int centerCellZ, int radiusCells, long untilTick) {
    /** Relaxation time of clouds, precipitation and storms inside an override, ticks. Commands should act fast. */
    public static final float RESPONSE_TIME_TICKS = 100.0F;

    /**
     * What each mode forces. Humidity is relative to saturation at the daily mean temperature.
     */
    public enum Mode {
        CLEAR(0.0F, 0.0F, 0.0F, 0.0F, 0.5F),
        CLOUDY(0.3F, 0.7F, 0.0F, 0.0F, 0.85F),
        RAIN(1.0F, 1.0F, 0.75F, 0.0F, 1.02F),
        STORM(1.0F, 1.0F, 1.0F, 1.0F, 1.02F);

        public final float lift;
        public final float cloudCover;
        public final float precipitation;
        public final float storm;
        public final float humidity;

        Mode(float lift, float cloudCover, float precipitation, float storm, float humidity) {
            this.lift = lift;
            this.cloudCover = cloudCover;
            this.precipitation = precipitation;
            this.storm = storm;
            this.humidity = humidity;
        }
    }

    public boolean covers(int cellX, int cellZ) {
        return Math.max(Math.abs(cellX - centerCellX), Math.abs(cellZ - centerCellZ)) <= radiusCells;
    }

    public boolean isActive(long gameTime) {
        return gameTime < untilTick;
    }
}
