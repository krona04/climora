package net.krona.climora.climate;

/**
 * State of one weather cell.
 * <p>
 * Base values are sampled once from the terrain. Simulated values are saved with the world.
 * Derived values (pressure, humidity, wind) are recomputed on every update and are not saved.
 */
public final class ClimateCell {
    /** Elevation of cells saved before it was tracked (data version 1). Such cells are re-sampled. */
    public static final int UNKNOWN_ELEVATION = Integer.MIN_VALUE;

    private final int cellX;
    private final int cellZ;

    // Base values, sampled from the terrain.
    /** Daily mean air temperature of the cell at its own elevation, °C (from the biome climate profiles). */
    private final float baseTemperature;
    /** Half of the day/night temperature swing of the cell, °C, or NaN if unknown (data version < 4). */
    private final float baseAmplitude;
    /** How humid the air over the cell tends to be, 0..1. */
    private final float baseHumidity;
    /** Share of the cell covered by ocean and river biomes, 0..1, or NaN if unknown (data version < 3). */
    private final float waterFraction;
    /** Mean surface height of the cell, or {@link #UNKNOWN_ELEVATION}. */
    private final int elevation;

    // Simulated values, saved.
    /** Air temperature at the mean surface height, °C. */
    private float temperature;
    /** Water vapour, g per kg of air, or NaN until the first update after an upgrade. */
    private float moisture = Float.NaN;
    /** 0..1 */
    private float cloudCover;
    /** 0..1 */
    private float precipitation;
    /** Thunderstorm intensity, 0..1. */
    private float storm;
    /**
     * Temperature of the air aloft at the mean surface height, °C, or NaN until the first update after an
     * upgrade. Carried by the wind, so it can differ from the ground air: warm air over cold ground.
     */
    private float columnTemperature = Float.NaN;
    /** How wet the ground is, 0..1. Puddles form above a threshold and dry out below it. */
    private float wetness;
    /** Game time of the last simulation step. */
    private long lastUpdateTick;

    // Derived values, not saved.
    private float pressure = (float) SynopticField.SEA_LEVEL_PRESSURE;
    private float relativeHumidity;
    /** Hail intensity, 0..1. */
    private float hail;
    /** Radiation fog near the ground, 0..1. */
    private float fog;
    /** Wind near the ground, m/s. */
    private float windX;
    private float windZ;
    /** Game time the derived values were computed, or {@code Long.MIN_VALUE} if never since loading. */
    private long derivedTick = Long.MIN_VALUE / 2;

    public ClimateCell(int cellX, int cellZ, float baseTemperature, float baseAmplitude, float baseHumidity,
                       float waterFraction, int elevation, float temperature, long lastUpdateTick) {
        this.cellX = cellX;
        this.cellZ = cellZ;
        this.baseTemperature = baseTemperature;
        this.baseAmplitude = baseAmplitude;
        this.baseHumidity = baseHumidity;
        this.waterFraction = waterFraction;
        this.elevation = elevation;
        this.temperature = temperature;
        this.lastUpdateTick = lastUpdateTick;
    }

    public int cellX() {
        return cellX;
    }

    public int cellZ() {
        return cellZ;
    }

    public long key() {
        return CellPos.key(cellX, cellZ);
    }

    public float baseTemperature() {
        return baseTemperature;
    }

    public float baseAmplitude() {
        return baseAmplitude;
    }

    public float baseHumidity() {
        return baseHumidity;
    }

    public float waterFraction() {
        return waterFraction;
    }

    public int elevation() {
        return elevation;
    }

    public boolean hasElevation() {
        return elevation != UNKNOWN_ELEVATION;
    }

    /** False for cells from old saves that still need to be sampled from the terrain again. */
    public boolean isFullySampled() {
        return hasElevation() && !Float.isNaN(waterFraction) && !Float.isNaN(baseAmplitude);
    }

    public float temperature() {
        return temperature;
    }

    public float moisture() {
        return moisture;
    }

    public float cloudCover() {
        return cloudCover;
    }

    public float precipitation() {
        return precipitation;
    }

    public float storm() {
        return storm;
    }

    public float columnTemperature() {
        return columnTemperature;
    }

    public float wetness() {
        return wetness;
    }

    public long lastUpdateTick() {
        return lastUpdateTick;
    }

    public float hail() {
        return hail;
    }

    public float fog() {
        return fog;
    }

    public float pressure() {
        return pressure;
    }

    public float relativeHumidity() {
        return relativeHumidity;
    }

    public float windX() {
        return windX;
    }

    public float windZ() {
        return windZ;
    }

    public float windSpeed() {
        return (float) Math.sqrt(windX * windX + windZ * windZ);
    }

    void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    void setWeather(float moisture, float cloudCover, float precipitation, float storm) {
        this.moisture = moisture;
        this.cloudCover = cloudCover;
        this.precipitation = precipitation;
        this.storm = storm;
    }

    void setGround(float columnTemperature, float wetness) {
        this.columnTemperature = columnTemperature;
        this.wetness = wetness;
    }

    void setHail(float hail, float fog) {
        this.hail = hail;
        this.fog = fog;
    }

    public long derivedTick() {
        return derivedTick;
    }

    void setDerived(float pressure, float relativeHumidity, float windX, float windZ, long gameTime) {
        this.pressure = pressure;
        this.relativeHumidity = relativeHumidity;
        this.windX = windX;
        this.windZ = windZ;
        this.derivedTick = gameTime;
    }

    void setLastUpdateTick(long lastUpdateTick) {
        this.lastUpdateTick = lastUpdateTick;
    }

    /** Takes over the simulated state of an older version of this cell. */
    void copyStateFrom(ClimateCell other) {
        this.temperature = other.temperature;
        this.moisture = other.moisture;
        this.cloudCover = other.cloudCover;
        this.precipitation = other.precipitation;
        this.storm = other.storm;
        this.columnTemperature = other.columnTemperature;
        this.wetness = other.wetness;
        this.lastUpdateTick = other.lastUpdateTick;
    }
}
