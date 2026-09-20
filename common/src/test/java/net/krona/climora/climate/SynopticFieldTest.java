package net.krona.climora.climate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SynopticFieldTest {

    @Test
    void sameSeedGivesSameWeather() {
        SynopticField a = new SynopticField(42L);
        SynopticField b = new SynopticField(42L);
        assertEquals(a.pressure(1234, -5678, 99_000L), b.pressure(1234, -5678, 99_000L));
        assertNotEquals(a.pressure(1234, -5678, 99_000L), new SynopticField(43L).pressure(1234, -5678, 99_000L));
    }

    @Test
    void pressureStaysInRealisticRangeAndHasHighsAndLows() {
        SynopticField field = new SynopticField(7L);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                double pressure = field.pressure(x * 500.0, z * 500.0, 10_000L);
                min = Math.min(min, pressure);
                max = Math.max(max, pressure);
            }
        }
        assertTrue(min >= SynopticField.SEA_LEVEL_PRESSURE - SynopticField.AMPLITUDE);
        assertTrue(max <= SynopticField.SEA_LEVEL_PRESSURE + SynopticField.AMPLITUDE);
        assertTrue(min < 1005.0, "there are lows");
        assertTrue(max > 1020.0, "there are highs");
    }

    @Test
    void pressureChangesSmoothlyInSpace() {
        SynopticField field = new SynopticField(11L);
        double here = field.pressure(0.0, 0.0, 5_000L);
        double nextCell = field.pressure(SimulationConstants.CELL_SIZE_BLOCKS, 0.0, 5_000L);
        assertTrue(Math.abs(here - nextCell) < 4.0, "neighbouring cells differ by a few hPa at most");
    }

    @Test
    void driftVelocityIsTheDerivativeOfTheOffset() {
        SynopticField field = new SynopticField(3L);
        long time = 123_456L;
        double velocityX = field.driftOffsetX(time + 1) - field.driftOffsetX(time);
        double velocityZ = field.driftOffsetZ(time + 1) - field.driftOffsetZ(time);
        assertEquals(velocityX, field.driftVelocityX(time), 1.0E-4);
        assertEquals(velocityZ, field.driftVelocityZ(time), 1.0E-4);
    }

    @Test
    void systemsNeverMoveBackwards() {
        SynopticField field = new SynopticField(99L);
        double mainX = field.driftVelocityX(0) + field.driftVelocityX((long) (SynopticField.WOBBLE_PERIOD_TICKS / 2));
        double mainZ = field.driftVelocityZ(0) + field.driftVelocityZ((long) (SynopticField.WOBBLE_PERIOD_TICKS / 2));
        for (long time = 0; time < SynopticField.WOBBLE_PERIOD_TICKS; time += 1000) {
            // The velocity always has a positive component along the main drift direction.
            double along = field.driftVelocityX(time) * mainX + field.driftVelocityZ(time) * mainZ;
            assertTrue(along > 0.0, "drift keeps its main direction at t=" + time);
        }
    }

    @Test
    void weatherTravelsWithTheDrift() {
        SynopticField field = new SynopticField(5L);
        long start = 1_000_000L;
        long later = start + 3000;
        double shiftX = field.driftOffsetX(later) - field.driftOffsetX(start);
        double shiftZ = field.driftOffsetZ(later) - field.driftOffsetZ(start);
        // The pattern also evolves, but following the drift it changes much less than at a fixed point.
        double movingError = 0.0;
        double fixedError = 0.0;
        for (int i = 0; i < 200; i++) {
            double x = i * 173.0 - 17000.0;
            double z = (i * 97) % 5000 - 2500.0;
            double before = field.pressure(x, z, start);
            movingError += Math.abs(before - field.pressure(x + shiftX, z + shiftZ, later));
            fixedError += Math.abs(before - field.pressure(x, z, later));
        }
        assertTrue(movingError < fixedError * 0.5, "moving " + movingError + " vs fixed " + fixedError);
    }
}
