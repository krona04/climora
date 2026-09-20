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

    /** Air temperature at a position, °C, or NaN if unknown. */
    float airTemperature(double x, double y, double z);

    /** Wind near the ground, m/s, X component. */
    float windX(double x, double z);

    /** Wind near the ground, m/s, Z component. */
    float windZ(double x, double z);
}
