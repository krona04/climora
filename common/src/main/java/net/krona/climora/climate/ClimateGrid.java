package net.krona.climora.climate;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.krona.climora.Climora;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * All weather cells of one dimension, stored in {@code data/climora_climate.dat}.
 * <p>
 * Cells are saved column-wise (one array per field) to keep the file small.
 * The format is described in docs/DESIGN.md.
 */
public final class ClimateGrid extends SavedData {
    public static final String DATA_NAME = Climora.MOD_ID + "_climate";
    /** The mod was called Tempestra until 0.1.1; worlds saved back then keep their data under this name. */
    public static final String LEGACY_DATA_NAME = "tempestra_climate";
    /** Bump on every format change and add an upgrade step to {@link #upgrade}. */
    public static final int DATA_VERSION = 6;

    static final String TAG_DATA_VERSION = "DataVersion";
    static final String TAG_CELL_SIZE = "CellSizeChunks";
    static final String TAG_KEYS = "Keys";
    static final String TAG_LAST_UPDATE = "LastUpdate";
    static final String TAG_BASE_TEMPERATURE = "BaseTemperature";
    static final String TAG_BASE_AMPLITUDE = "BaseAmplitude";
    static final String TAG_BASE_HUMIDITY = "BaseHumidity";
    static final String TAG_WATER_FRACTION = "WaterFraction";
    static final String TAG_ELEVATION = "Elevation";
    static final String TAG_TEMPERATURE = "Temperature";
    static final String TAG_MOISTURE = "Moisture";
    static final String TAG_CLOUD_COVER = "CloudCover";
    static final String TAG_PRECIPITATION = "Precipitation";
    static final String TAG_STORM = "Storm";
    static final String TAG_COLUMN_TEMPERATURE = "ColumnTemperature";
    static final String TAG_WETNESS = "Wetness";
    static final String TAG_WEATHER_TIME = "WeatherTime";

    private final Long2ObjectOpenHashMap<ClimateCell> cells = new Long2ObjectOpenHashMap<>();
    /**
     * Clock of the weather systems, ticks. It runs at {@code weatherSpeed} times game time, so the speed
     * can change without the pressure pattern jumping. NaN until the simulation sets it (new or upgraded data).
     */
    private double weatherTime = Double.NaN;
    /** Data written by a newer version of the mod. Kept untouched and written back as is. */
    @Nullable
    private CompoundTag unsupportedData;

    public static SavedData.Factory<ClimateGrid> factory() {
        return new SavedData.Factory<>(ClimateGrid::new, ClimateGrid::load, null);
    }

    @Nullable
    public ClimateCell get(long key) {
        return cells.get(key);
    }

    public boolean contains(long key) {
        return cells.containsKey(key);
    }

    public void put(ClimateCell cell) {
        cells.put(cell.key(), cell);
    }

    /** Takes over the cells of a grid saved under the old mod name. */
    public void takeOver(ClimateGrid legacy) {
        cells.putAll(legacy.cells);
        setDirty();
    }

    public int size() {
        return cells.size();
    }

    public double weatherTime() {
        return weatherTime;
    }

    public void setWeatherTime(double weatherTime) {
        this.weatherTime = weatherTime;
    }

    /** True when the saved data is from a newer mod version; the simulation must not run. */
    public boolean isReadOnly() {
        return unsupportedData != null;
    }

    static ClimateGrid load(CompoundTag tag, @Nullable HolderLookup.Provider registries) {
        ClimateGrid grid = new ClimateGrid();
        int version = tag.getInt(TAG_DATA_VERSION);

        if (version > DATA_VERSION) {
            Climora.LOGGER.error("Climate data has version {}, but this Climora build supports up to {}. "
                    + "The simulation is disabled for this dimension and the data is kept unchanged.", version, DATA_VERSION);
            grid.unsupportedData = tag.copy();
            return grid;
        }
        if (version < 1) {
            Climora.LOGGER.warn("Climate data has no valid version, starting with an empty grid.");
            return grid;
        }

        tag = upgrade(tag, version);

        int cellSize = tag.getInt(TAG_CELL_SIZE);
        if (cellSize != SimulationConstants.CELL_SIZE_CHUNKS) {
            Climora.LOGGER.warn("Climate data uses {}-chunk cells, current size is {}. Cells will be rebuilt.",
                    cellSize, SimulationConstants.CELL_SIZE_CHUNKS);
            return grid;
        }

        long[] keys = tag.getLongArray(TAG_KEYS);
        long[] lastUpdate = tag.getLongArray(TAG_LAST_UPDATE);
        int[] baseTemperature = tag.getIntArray(TAG_BASE_TEMPERATURE);
        int[] baseAmplitude = tag.getIntArray(TAG_BASE_AMPLITUDE);
        int[] baseHumidity = tag.getIntArray(TAG_BASE_HUMIDITY);
        int[] waterFraction = tag.getIntArray(TAG_WATER_FRACTION);
        int[] elevation = tag.getIntArray(TAG_ELEVATION);
        int[] temperature = tag.getIntArray(TAG_TEMPERATURE);
        int[] moisture = tag.getIntArray(TAG_MOISTURE);
        int[] cloudCover = tag.getIntArray(TAG_CLOUD_COVER);
        int[] precipitation = tag.getIntArray(TAG_PRECIPITATION);
        int[] storm = tag.getIntArray(TAG_STORM);
        int[] columnTemperature = tag.getIntArray(TAG_COLUMN_TEMPERATURE);
        int[] wetness = tag.getIntArray(TAG_WETNESS);
        grid.weatherTime = tag.contains(TAG_WEATHER_TIME) ? tag.getDouble(TAG_WEATHER_TIME) : Double.NaN;

        int count = keys.length;
        if (lastUpdate.length != count || baseTemperature.length != count || baseAmplitude.length != count
                || baseHumidity.length != count
                || waterFraction.length != count || elevation.length != count || temperature.length != count
                || moisture.length != count || cloudCover.length != count || precipitation.length != count
                || storm.length != count || columnTemperature.length != count || wetness.length != count) {
            Climora.LOGGER.error("Climate data is corrupted (array lengths differ). Cells will be rebuilt.");
            return grid;
        }

        for (int i = 0; i < count; i++) {
            ClimateCell cell = new ClimateCell(
                    CellPos.x(keys[i]),
                    CellPos.z(keys[i]),
                    Float.intBitsToFloat(baseTemperature[i]),
                    Float.intBitsToFloat(baseAmplitude[i]),
                    Float.intBitsToFloat(baseHumidity[i]),
                    Float.intBitsToFloat(waterFraction[i]),
                    elevation[i],
                    Float.intBitsToFloat(temperature[i]),
                    lastUpdate[i]);
            cell.setWeather(
                    Float.intBitsToFloat(moisture[i]),
                    Float.intBitsToFloat(cloudCover[i]),
                    Float.intBitsToFloat(precipitation[i]),
                    Float.intBitsToFloat(storm[i]));
            cell.setGround(Float.intBitsToFloat(columnTemperature[i]), Float.intBitsToFloat(wetness[i]));
            grid.put(cell);
        }
        return grid;
    }

    /** Upgrades saved data step by step from {@code fromVersion} to {@link #DATA_VERSION}. */
    private static CompoundTag upgrade(CompoundTag tag, int fromVersion) {
        for (int version = fromVersion; version < DATA_VERSION; version++) {
            switch (version) {
                case 1 -> upgradeFrom1(tag);
                case 2 -> upgradeFrom2(tag);
                case 3 -> upgradeFrom3(tag);
                case 4 -> upgradeFrom4(tag);
                case 5 -> upgradeFrom5(tag);
                default -> throw new IllegalStateException("Missing climate data upgrade from version " + version);
            }
        }
        return tag;
    }

    /** Version 2 adds cell elevation. Old cells get an unknown elevation and are re-sampled when active. */
    private static void upgradeFrom1(CompoundTag tag) {
        tag.putIntArray(TAG_ELEVATION, filledInts(cellCount(tag), ClimateCell.UNKNOWN_ELEVATION));
    }

    /**
     * Version 3 adds weather: water fraction (unknown, so cells are re-sampled), moisture (unknown, so it
     * starts at equilibrium) and cloud cover, precipitation and storm (clear sky).
     */
    private static void upgradeFrom2(CompoundTag tag) {
        int count = cellCount(tag);
        int nan = Float.floatToIntBits(Float.NaN);
        int zero = Float.floatToIntBits(0.0F);
        tag.putIntArray(TAG_WATER_FRACTION, filledInts(count, nan));
        tag.putIntArray(TAG_MOISTURE, filledInts(count, nan));
        tag.putIntArray(TAG_CLOUD_COVER, filledInts(count, zero));
        tag.putIntArray(TAG_PRECIPITATION, filledInts(count, zero));
        tag.putIntArray(TAG_STORM, filledInts(count, zero));
    }

    /** Version 4 adds the day/night amplitude from the biome climate profiles: cells are re-sampled. */
    private static void upgradeFrom3(CompoundTag tag) {
        tag.putIntArray(TAG_BASE_AMPLITUDE, filledInts(cellCount(tag), Float.floatToIntBits(Float.NaN)));
    }

    /**
     * Version 5 (0.2) adds the air aloft (unknown, so it starts at the equilibrium of each cell), wet ground
     * (dry) and the weather clock (missing, so the simulation starts it at the current game time: the
     * pressure pattern of 0.1 was driven by game time, so it continues without a jump).
     */
    private static void upgradeFrom4(CompoundTag tag) {
        int count = cellCount(tag);
        tag.putIntArray(TAG_COLUMN_TEMPERATURE, filledInts(count, Float.floatToIntBits(Float.NaN)));
        tag.putIntArray(TAG_WETNESS, filledInts(count, Float.floatToIntBits(0.0F)));
    }

    /**
     * Version 6 changes the climate of snow-covered biomes (a small day/night swing that keeps them frozen).
     * The base values are re-sampled from the terrain; the simulated weather of the cells stays.
     */
    private static void upgradeFrom5(CompoundTag tag) {
        tag.putIntArray(TAG_BASE_AMPLITUDE, filledInts(cellCount(tag), Float.floatToIntBits(Float.NaN)));
    }

    private static int cellCount(CompoundTag tag) {
        return tag.getLongArray(TAG_KEYS).length;
    }

    private static int[] filledInts(int count, int value) {
        int[] values = new int[count];
        Arrays.fill(values, value);
        return values;
    }

    @Override
    public CompoundTag save(CompoundTag tag, @Nullable HolderLookup.Provider registries) {
        if (unsupportedData != null) {
            return tag.merge(unsupportedData);
        }

        int count = cells.size();
        long[] keys = new long[count];
        long[] lastUpdate = new long[count];
        int[] baseTemperature = new int[count];
        int[] baseAmplitude = new int[count];
        int[] baseHumidity = new int[count];
        int[] waterFraction = new int[count];
        int[] elevation = new int[count];
        int[] temperature = new int[count];
        int[] moisture = new int[count];
        int[] cloudCover = new int[count];
        int[] precipitation = new int[count];
        int[] storm = new int[count];
        int[] columnTemperature = new int[count];
        int[] wetness = new int[count];

        int i = 0;
        for (ClimateCell cell : cells.values()) {
            keys[i] = cell.key();
            lastUpdate[i] = cell.lastUpdateTick();
            baseTemperature[i] = Float.floatToIntBits(cell.baseTemperature());
            baseAmplitude[i] = Float.floatToIntBits(cell.baseAmplitude());
            baseHumidity[i] = Float.floatToIntBits(cell.baseHumidity());
            waterFraction[i] = Float.floatToIntBits(cell.waterFraction());
            elevation[i] = cell.elevation();
            temperature[i] = Float.floatToIntBits(cell.temperature());
            moisture[i] = Float.floatToIntBits(cell.moisture());
            cloudCover[i] = Float.floatToIntBits(cell.cloudCover());
            precipitation[i] = Float.floatToIntBits(cell.precipitation());
            storm[i] = Float.floatToIntBits(cell.storm());
            columnTemperature[i] = Float.floatToIntBits(cell.columnTemperature());
            wetness[i] = Float.floatToIntBits(cell.wetness());
            i++;
        }

        tag.putInt(TAG_DATA_VERSION, DATA_VERSION);
        tag.putInt(TAG_CELL_SIZE, SimulationConstants.CELL_SIZE_CHUNKS);
        tag.putLongArray(TAG_KEYS, keys);
        tag.putLongArray(TAG_LAST_UPDATE, lastUpdate);
        tag.putIntArray(TAG_BASE_TEMPERATURE, baseTemperature);
        tag.putIntArray(TAG_BASE_AMPLITUDE, baseAmplitude);
        tag.putIntArray(TAG_BASE_HUMIDITY, baseHumidity);
        tag.putIntArray(TAG_WATER_FRACTION, waterFraction);
        tag.putIntArray(TAG_ELEVATION, elevation);
        tag.putIntArray(TAG_TEMPERATURE, temperature);
        tag.putIntArray(TAG_MOISTURE, moisture);
        tag.putIntArray(TAG_CLOUD_COVER, cloudCover);
        tag.putIntArray(TAG_PRECIPITATION, precipitation);
        tag.putIntArray(TAG_STORM, storm);
        tag.putIntArray(TAG_COLUMN_TEMPERATURE, columnTemperature);
        tag.putIntArray(TAG_WETNESS, wetness);
        if (!Double.isNaN(weatherTime)) {
            tag.putDouble(TAG_WEATHER_TIME, weatherTime);
        }
        return tag;
    }
}
