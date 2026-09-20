package net.krona.climora.climate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClimateModelTest {
    private static final float EPSILON = 1.0E-3F;

    @Test
    void biomeTemperatureMapsToPlausibleCelsius() {
        assertEquals(14.0F, ClimateModel.biomeToCelsius(0.8F), EPSILON); // plains
        assertEquals(38.0F, ClimateModel.biomeToCelsius(2.0F), EPSILON); // desert
        assertTrue(ClimateModel.biomeToCelsius(-0.5F) < 0.0F); // snowy taiga
    }

    @Test
    void dryCellsSwingMoreThanHumidOnes() {
        assertEquals(ClimateModel.DRY_DIURNAL_AMPLITUDE, ClimateModel.diurnalAmplitude(0.0F), EPSILON);
        assertEquals(ClimateModel.HUMID_DIURNAL_AMPLITUDE, ClimateModel.diurnalAmplitude(1.0F), EPSILON);
        assertEquals(ClimateModel.DRY_DIURNAL_AMPLITUDE, ClimateModel.diurnalAmplitude(-3.0F), EPSILON);
    }

    @Test
    void warmestInAfternoonAndColdestAtNight() {
        float amplitude = 9.0F;
        long warmest = ClimateModel.WARMEST_DAY_TIME;
        long coldest = warmest + ClimateModel.TICKS_PER_DAY / 2;

        assertEquals(amplitude, ClimateModel.diurnalOffset(amplitude, warmest), EPSILON);
        assertEquals(-amplitude, ClimateModel.diurnalOffset(amplitude, coldest), EPSILON);
        // Day time keeps growing across days, the cycle must repeat.
        assertEquals(amplitude, ClimateModel.diurnalOffset(amplitude, warmest + 5L * ClimateModel.TICKS_PER_DAY), EPSILON);
    }

    @Test
    void higherGroundIsColder() {
        assertEquals(0.0F, ClimateModel.altitudeOffset(64.0F, 64.0F), EPSILON);
        assertEquals(-6.0F, ClimateModel.altitudeOffset(64.0F, 264.0F), EPSILON);
        assertEquals(1.5F, ClimateModel.altitudeOffset(64.0F, 14.0F), EPSILON);

        long noon = ClimateModel.WARMEST_DAY_TIME;
        float valley = ClimateModel.targetTemperature(14.0F, 8.0F, noon);
        float hill = ClimateModel.targetTemperature(14.0F + ClimateModel.altitudeOffset(64.0F, 214.0F), 8.0F, noon);
        assertEquals(valley - 150.0F * ClimateModel.LAPSE_RATE_PER_BLOCK, hill, EPSILON);
    }

    @Test
    void relaxationMovesTowardsTargetWithoutOvershoot() {
        float tau = SimulationConstants.THERMAL_TIME_CONSTANT_TICKS;

        assertEquals(10.0F, ClimateModel.relax(10.0F, 20.0F, 0, tau), EPSILON);
        assertEquals(10.0F, ClimateModel.relax(10.0F, 20.0F, -100, tau), EPSILON);

        float afterOneTau = ClimateModel.relax(10.0F, 20.0F, (long) tau, tau);
        assertEquals(10.0F + 10.0F * (1.0F - (float) Math.exp(-1.0)), afterOneTau, EPSILON);

        float afterLongPause = ClimateModel.relax(10.0F, 20.0F, 10_000_000L, tau);
        assertEquals(20.0F, afterLongPause, EPSILON);
    }

    @Test
    void manySmallStepsMatchOneLargeStep() {
        float tau = SimulationConstants.THERMAL_TIME_CONSTANT_TICKS;
        float stepped = 0.0F;
        for (int i = 0; i < 100; i++) {
            stepped = ClimateModel.relax(stepped, 30.0F, 25, tau);
        }
        float single = ClimateModel.relax(0.0F, 30.0F, 2500, tau);
        assertEquals(single, stepped, 1.0E-2F);
    }
}
