package net.krona.climora.climate;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClimateInterpolationTest {
    private static final float EPSILON = 1.0E-3F;
    private static final int SIZE = SimulationConstants.CELL_SIZE_BLOCKS;

    private final Map<Long, ClimateCell> cells = new HashMap<>();

    private void cell(int x, int z, float temperature, int elevation) {
        cells.put(CellPos.key(x, z), new ClimateCell(x, z, temperature, 8.0F, 0.5F, 0.0F, elevation, temperature, 0L));
    }

    private ClimateInterpolation.Sample sample(double x, double z) {
        return ClimateInterpolation.sample(cells::get, x, z);
    }

    @Test
    void rainNeverBlendsOutFromUnderTheClouds() {
        // An overcast, raining cell next to a clear one: the shape /climora weather rain makes, and the shape
        // the edge of a natural shower takes. Blending cover and rain apart lets the cover fall away first.
        cell(0, 0, 15.0F, 64);
        cell(1, 0, 15.0F, 64);
        cells.get(CellPos.key(0, 0)).setWeather(0.02F, 1.0F, 0.5F, 0.0F);
        cells.get(CellPos.key(1, 0)).setWeather(0.02F, 0.0F, 0.0F, 0.0F);

        double from = CellPos.centerBlock(0);
        double to = CellPos.centerBlock(1);
        for (int step = 0; step <= 20; step++) {
            double x = from + (to - from) * step / 20.0;
            ClimateInterpolation.Sample sample = sample(x, CellPos.centerBlock(0));
            assertNotNull(sample);
            assertTrue(sample.cloudCover() >= AtmosphereModel.overcastFor(sample.precipitation()) - EPSILON,
                    "rain from a half-empty sky at x=" + x + ": cover " + sample.cloudCover()
                            + ", precipitation " + sample.precipitation());
        }
    }

    @Test
    void noCellsGivesNoSample() {
        assertNull(sample(0, 0));
    }

    @Test
    void cellCenterMatchesTheCell() {
        cell(0, 0, 10.0F, 64);
        cell(1, 0, 20.0F, 96);
        cell(0, 1, 30.0F, 64);
        cell(1, 1, 40.0F, 64);

        ClimateInterpolation.Sample center = sample(CellPos.centerBlock(0), CellPos.centerBlock(0));
        assertNotNull(center);
        assertEquals(10.0F, center.surfaceTemperature(), EPSILON);
        assertEquals(64.0F, center.surfaceElevation(), EPSILON);
    }

    @Test
    void halfwayBetweenCentersIsTheMean() {
        cell(0, 0, 10.0F, 64);
        cell(1, 0, 20.0F, 96);

        ClimateInterpolation.Sample border = sample(SIZE, CellPos.centerBlock(0));
        assertNotNull(border);
        assertEquals(15.0F, border.surfaceTemperature(), EPSILON);
        assertEquals(80.0F, border.surfaceElevation(), EPSILON);
    }

    @Test
    void temperatureIsContinuousAcrossCellBorder() {
        cell(0, 0, 10.0F, 64);
        cell(1, 0, 20.0F, 64);
        cell(0, 1, 12.0F, 64);
        cell(1, 1, 22.0F, 64);

        // The interpolation switches to a different set of four cells exactly at a cell center.
        double switchLine = CellPos.centerBlock(1);
        float left = sample(switchLine - 0.01, 100).surfaceTemperature();
        float right = sample(switchLine + 0.01, 100).surfaceTemperature();
        assertEquals(left, right, 0.01F);
    }

    @Test
    void missingNeighboursAreSkipped() {
        cell(0, 0, 10.0F, 64);

        ClimateInterpolation.Sample sample = sample(SIZE, SIZE);
        assertNotNull(sample);
        assertEquals(1, sample.cellCount());
        assertEquals(10.0F, sample.surfaceTemperature(), EPSILON);
    }

    @Test
    void airCoolsAboveTheSurface() {
        cell(0, 0, 10.0F, 64);
        ClimateInterpolation.Sample sample = sample(CellPos.centerBlock(0), CellPos.centerBlock(0));
        assertNotNull(sample);

        assertEquals(10.0F, sample.airTemperature(64), EPSILON);
        assertEquals(10.0F - 100 * ClimateModel.LAPSE_RATE_PER_BLOCK, sample.airTemperature(164), EPSILON);
        assertTrue(sample.airTemperature(40) > 10.0F);
    }

    @Test
    void unknownElevationIgnoresHeight() {
        cells.put(CellPos.key(0, 0), new ClimateCell(0, 0, 10.0F, 8.0F, 0.5F, 0.0F, ClimateCell.UNKNOWN_ELEVATION, 10.0F, 0L));
        ClimateInterpolation.Sample sample = sample(CellPos.centerBlock(0), CellPos.centerBlock(0));
        assertNotNull(sample);

        assertTrue(Float.isNaN(sample.surfaceElevation()));
        assertEquals(10.0F, sample.airTemperature(250), EPSILON);
    }
}
