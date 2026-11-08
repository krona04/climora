package net.krona.climora.weather;

/**
 * Local weather as seen by one side: the server reads its simulation, the client reads what the
 * server sent. Values are interpolated between cells. Precipitation and storm are 0 where unknown.
 */
public interface WeatherView {
    /** Precipitation intensity at a horizontal position, 0..1. */
    float precipitation(double x, double z);

    /** Thunderstorm intensity at a horizontal position, 0..1. */
    float storm(double x, double z);

    /** Hail intensity at a horizontal position, 0..1. */
    float hail(double x, double z);

    /** How wet the ground is, 0..1. Only the server tracks it; the client sees 0. */
    default float wetness(double x, double z) {
        return 0.0F;
    }

    /** Air temperature at a position, °C, or NaN if unknown. */
    float airTemperature(double x, double y, double z);

    /**
     * Temperature of the air aloft above a position, brought to the same height, °C, or NaN if unknown.
     * Warmer than {@link #airTemperature} when warm air has blown in over cold ground.
     */
    float columnTemperature(double x, double y, double z);

    /** Wind near the ground, m/s, X component. */
    float windX(double x, double z);

    /** Wind near the ground, m/s, Z component. */
    float windZ(double x, double z);
}
