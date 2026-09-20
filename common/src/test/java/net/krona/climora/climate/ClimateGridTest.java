package net.krona.climora.climate;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClimateGridTest {

    @Test
    void cellsSurviveSaveAndLoad() {
        ClimateGrid grid = new ClimateGrid();
        ClimateCell original = new ClimateCell(3, -4, 14.5F, 9.0F, 0.4F, 0.2F, 72, 17.25F, 123_456L);
        original.setWeather(9.5F, 0.6F, 0.3F, 0.1F);
        grid.put(original);
        grid.put(new ClimateCell(-100, 250, -12.0F, 7.0F, 0.9F, 1.0F, 180, -15.5F, 42L));

        ClimateGrid loaded = ClimateGrid.load(grid.save(new CompoundTag(), null), null);

        assertFalse(loaded.isReadOnly());
        assertEquals(2, loaded.size());
        ClimateCell cell = loaded.get(CellPos.key(3, -4));
        assertNotNull(cell);
        assertEquals(3, cell.cellX());
        assertEquals(-4, cell.cellZ());
        assertEquals(14.5F, cell.baseTemperature());
        assertEquals(9.0F, cell.baseAmplitude());
        assertEquals(0.4F, cell.baseHumidity());
        assertEquals(0.2F, cell.waterFraction());
        assertEquals(72, cell.elevation());
        assertEquals(17.25F, cell.temperature());
        assertEquals(9.5F, cell.moisture());
        assertEquals(0.6F, cell.cloudCover());
        assertEquals(0.3F, cell.precipitation());
        assertEquals(0.1F, cell.storm());
        assertEquals(123_456L, cell.lastUpdateTick());
        assertTrue(cell.isFullySampled());
        assertNotNull(loaded.get(CellPos.key(-100, 250)));
    }

    @Test
    void savedDataCarriesFormatVersionAndCellSize() {
        CompoundTag tag = new ClimateGrid().save(new CompoundTag(), null);
        assertEquals(ClimateGrid.DATA_VERSION, tag.getInt(ClimateGrid.TAG_DATA_VERSION));
        assertEquals(SimulationConstants.CELL_SIZE_CHUNKS, tag.getInt(ClimateGrid.TAG_CELL_SIZE));
    }

    @Test
    void version1DataIsUpgradedToCurrent() {
        CompoundTag v1 = new CompoundTag();
        v1.putInt(ClimateGrid.TAG_DATA_VERSION, 1);
        v1.putInt(ClimateGrid.TAG_CELL_SIZE, SimulationConstants.CELL_SIZE_CHUNKS);
        v1.putLongArray(ClimateGrid.TAG_KEYS, new long[]{CellPos.key(1, 2), CellPos.key(-5, 7)});
        v1.putLongArray(ClimateGrid.TAG_LAST_UPDATE, new long[]{100L, 200L});
        v1.putIntArray(ClimateGrid.TAG_BASE_TEMPERATURE, floats(14.0F, -2.0F));
        v1.putIntArray(ClimateGrid.TAG_BASE_HUMIDITY, floats(0.4F, 0.5F));
        v1.putIntArray(ClimateGrid.TAG_TEMPERATURE, floats(15.5F, -3.0F));

        ClimateGrid grid = ClimateGrid.load(v1, null);

        assertEquals(2, grid.size());
        ClimateCell cell = grid.get(CellPos.key(1, 2));
        assertNotNull(cell);
        assertFalse(cell.hasElevation());
        assertFalse(cell.isFullySampled());
        assertTrue(Float.isNaN(cell.moisture()));
        assertEquals(15.5F, cell.temperature());
        assertEquals(100L, cell.lastUpdateTick());

        CompoundTag resaved = grid.save(new CompoundTag(), null);
        assertEquals(ClimateGrid.DATA_VERSION, resaved.getInt(ClimateGrid.TAG_DATA_VERSION));
        assertEquals(2, resaved.getIntArray(ClimateGrid.TAG_ELEVATION).length);
        assertEquals(2, resaved.getIntArray(ClimateGrid.TAG_STORM).length);
        assertEquals(2, resaved.getIntArray(ClimateGrid.TAG_BASE_AMPLITUDE).length);
    }

    @Test
    void version2DataGetsClearWeatherAndIsResampled() {
        ClimateGrid grid = singleCellGrid();
        CompoundTag v2 = grid.save(new CompoundTag(), null);
        v2.putInt(ClimateGrid.TAG_DATA_VERSION, 2);
        for (String tag : new String[]{ClimateGrid.TAG_WATER_FRACTION, ClimateGrid.TAG_MOISTURE, ClimateGrid.TAG_CLOUD_COVER,
                ClimateGrid.TAG_PRECIPITATION, ClimateGrid.TAG_STORM}) {
            v2.remove(tag);
        }

        ClimateCell cell = ClimateGrid.load(v2, null).get(CellPos.key(0, 0));

        assertNotNull(cell);
        assertEquals(64, cell.elevation());
        assertTrue(Float.isNaN(cell.waterFraction()));
        assertFalse(cell.isFullySampled());
        assertTrue(Float.isNaN(cell.moisture()));
        assertEquals(0.0F, cell.cloudCover());
        assertEquals(0.0F, cell.precipitation());
        assertEquals(0.0F, cell.storm());
    }

    @Test
    void version3DataIsResampledForTheBiomeProfiles() {
        CompoundTag v3 = singleCellGrid().save(new CompoundTag(), null);
        v3.putInt(ClimateGrid.TAG_DATA_VERSION, 3);
        v3.remove(ClimateGrid.TAG_BASE_AMPLITUDE);

        ClimateCell cell = ClimateGrid.load(v3, null).get(CellPos.key(0, 0));

        assertNotNull(cell);
        assertTrue(Float.isNaN(cell.baseAmplitude()));
        assertFalse(cell.isFullySampled());
        assertEquals(10.0F, cell.temperature(), 1.0E-3F);
    }

    @Test
    void dataFromNewerVersionIsKeptUntouched() {
        CompoundTag future = new CompoundTag();
        future.putInt(ClimateGrid.TAG_DATA_VERSION, ClimateGrid.DATA_VERSION + 1);
        future.putString("SomethingNew", "keep me");

        ClimateGrid grid = ClimateGrid.load(future, null);
        assertTrue(grid.isReadOnly());
        assertEquals(0, grid.size());
        assertEquals(future, grid.save(new CompoundTag(), null));
    }

    @Test
    void differentCellSizeStartsEmpty() {
        CompoundTag tag = singleCellGrid().save(new CompoundTag(), null);
        tag.putInt(ClimateGrid.TAG_CELL_SIZE, SimulationConstants.CELL_SIZE_CHUNKS * 2);

        ClimateGrid loaded = ClimateGrid.load(tag, null);
        assertFalse(loaded.isReadOnly());
        assertEquals(0, loaded.size());
    }

    @Test
    void corruptedArraysStartEmpty() {
        CompoundTag tag = singleCellGrid().save(new CompoundTag(), null);
        tag.putIntArray(ClimateGrid.TAG_TEMPERATURE, new int[0]);

        assertEquals(0, ClimateGrid.load(tag, null).size());
    }

    @Test
    void missingVersionStartsEmpty() {
        assertEquals(0, ClimateGrid.load(new CompoundTag(), null).size());
    }

    private static ClimateGrid singleCellGrid() {
        ClimateGrid grid = new ClimateGrid();
        grid.put(new ClimateCell(0, 0, 10.0F, 8.0F, 0.5F, 0.0F, 64, 10.0F, 0L));
        return grid;
    }

    private static int[] floats(float... values) {
        int[] bits = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            bits[i] = Float.floatToIntBits(values[i]);
        }
        return bits;
    }
}
