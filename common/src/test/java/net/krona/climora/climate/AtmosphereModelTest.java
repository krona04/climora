package net.krona.climora.climate;

import net.krona.climora.weather.LocalWeather;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereModelTest {
    private static final float EPSILON = 1.0E-3F;

    @Test
    void magnusFormulaMatchesReferenceValues() {
        assertEquals(6.112, AtmosphereModel.saturationVaporPressure(0.0), 1.0E-3);
        assertEquals(23.4, AtmosphereModel.saturationVaporPressure(20.0), 0.1);
        // Saturated air at 20 °C and 1013 hPa holds about 14.7 g/kg.
        assertEquals(14.7, AtmosphereModel.saturationMixingRatio(20.0, 1013.0), 0.2);
        assertTrue(AtmosphereModel.saturationMixingRatio(-10.0, 1013.0) < AtmosphereModel.saturationMixingRatio(10.0, 1013.0));
    }

    @Test
    void liftGrowsAsPressureFalls() {
        assertEquals(0.0F, AtmosphereModel.lift(1030.0F), EPSILON);
        assertEquals(0.0F, AtmosphereModel.lift(AtmosphereModel.LIFT_START_PRESSURE), EPSILON);
        assertEquals(1.0F, AtmosphereModel.lift(AtmosphereModel.LIFT_FULL_PRESSURE), EPSILON);
        assertTrue(AtmosphereModel.lift(1005.0F) > AtmosphereModel.lift(1010.0F));
    }

    @Test
    void noPrecipitationWithoutRisingAir() {
        assertEquals(0.0F, AtmosphereModel.precipitationTarget(1.0F, 0.0F), EPSILON);
        assertEquals(0.0F, AtmosphereModel.precipitationTarget(1.0F, 0.2F), EPSILON);
    }

    @Test
    void plainsRainInDeepLowButDesertsStayDry() {
        float deepLow = 1.0F;
        float plains = AtmosphereModel.equilibriumHumidity(0.4F, 0.0F, deepLow);
        float desert = AtmosphereModel.equilibriumHumidity(0.0F, 0.0F, deepLow);
        float jungleInHigh = AtmosphereModel.equilibriumHumidity(0.9F, 0.0F, 0.0F);

        assertTrue(AtmosphereModel.precipitationTarget(plains, deepLow) > 0.5F, "plains in a deep low");
        assertEquals(0.0F, AtmosphereModel.precipitationTarget(desert, deepLow), EPSILON, "desert in a deep low");
        assertEquals(0.0F, AtmosphereModel.precipitationTarget(jungleInHigh, 0.0F), EPSILON, "jungle in a high");
        assertTrue(AtmosphereModel.cloudTarget(desert, deepLow) > 0.0F, "deserts still get some clouds");
    }

    @Test
    void highsHaveFewerCloudsThanLows() {
        assertTrue(AtmosphereModel.cloudTarget(0.9F, 0.0F) < AtmosphereModel.cloudTarget(0.9F, 1.0F));
        assertEquals(0.0F, AtmosphereModel.cloudTarget(0.4F, 1.0F), EPSILON);
    }

    @Test
    void stormsNeedWarmHumidRisingAirAndRain() {
        assertTrue(AtmosphereModel.stormTarget(28.0F, 1.0F, 1.0F, 0.9F, 0.0F) > 0.9F);
        assertTrue(AtmosphereModel.stormTarget(28.0F, 1.0F, 0.0F, 0.9F, 1.0F) > 0.5F, "afternoon heating triggers storms");
        assertEquals(0.0F, AtmosphereModel.stormTarget(5.0F, 1.0F, 1.0F, 0.9F, 0.0F), EPSILON, "too cold");
        assertEquals(0.0F, AtmosphereModel.stormTarget(28.0F, 1.0F, 1.0F, 0.1F, 0.0F), EPSILON, "no rain");
        assertEquals(0.0F, AtmosphereModel.stormTarget(28.0F, 0.6F, 1.0F, 0.9F, 0.0F), EPSILON, "too dry");
    }

    @Test
    void rainAlwaysComesWithAClosedCloudDeck() {
        assertEquals(LocalWeather.PRECIPITATION_THRESHOLD, AtmosphereModel.OVERCAST_PRECIPITATION, EPSILON,
                "the sky must be overcast by the intensity at which rain starts being drawn");
        assertEquals(0.0F, AtmosphereModel.overcastFor(0.0F), EPSILON);
        assertEquals(1.0F, AtmosphereModel.overcastFor(LocalWeather.PRECIPITATION_THRESHOLD), EPSILON);
        assertEquals(1.0F, AtmosphereModel.overcastFor(1.0F), EPSILON);
        assertTrue(AtmosphereModel.overcastFor(LocalWeather.PRECIPITATION_THRESHOLD / 2.0F) > 0.4F,
                "clouds gather before the first drop");
    }

    @Test
    void rainThroughWarmAirOntoFrozenGroundFreezes() {
        assertEquals(AtmosphereModel.PrecipitationType.FREEZING_RAIN, AtmosphereModel.precipitationType(-2.0F, 5.0F));
        assertEquals(AtmosphereModel.PrecipitationType.SNOW, AtmosphereModel.precipitationType(-2.0F, 1.0F),
                "air aloft too cold to melt the snow");
        assertEquals(AtmosphereModel.PrecipitationType.SNOW, AtmosphereModel.precipitationType(-2.0F, Float.NaN),
                "unknown air aloft: plain snow");
        assertEquals(AtmosphereModel.PrecipitationType.SLEET, AtmosphereModel.precipitationType(1.0F, 8.0F));
        assertEquals(AtmosphereModel.PrecipitationType.RAIN, AtmosphereModel.precipitationType(3.0F, 8.0F));
        assertTrue(AtmosphereModel.PrecipitationType.FREEZING_RAIN.isLiquid());
        assertTrue(!AtmosphereModel.PrecipitationType.SNOW.isLiquid() && !AtmosphereModel.PrecipitationType.NONE.isLiquid());
    }

    @Test
    void hailNeedsTheStrongestStormOnAHotAfternoonAndAnUpdraftSurge() {
        assertEquals(AtmosphereModel.HAIL_MAX, AtmosphereModel.hailTarget(1.0F, 1.0F, 30.0F, 1.0F), EPSILON);
        assertEquals(0.0F, AtmosphereModel.hailTarget(0.6F, 1.0F, 30.0F, 1.0F), EPSILON, "ordinary thunderstorm");
        assertEquals(0.0F, AtmosphereModel.hailTarget(1.0F, 0.6F, 30.0F, 1.0F), EPSILON, "weak updraft");
        assertEquals(0.0F, AtmosphereModel.hailTarget(1.0F, 1.0F, 18.0F, 1.0F), EPSILON, "a storm on a cool day rains");
        assertEquals(0.0F, AtmosphereModel.hailTarget(1.0F, 1.0F, 30.0F, 0.0F), EPSILON, "between surges");
        assertTrue(AtmosphereModel.hailTarget(1.0F, 1.0F, 25.0F, 1.0F) > 0.3F, "a hot afternoon storm can hail");
    }

    @Test
    void evenAPerfectHailStormHailsOnlyPartOfTheTimeAndPlace() {
        // The strongest storm on the hottest day: how much of its cells and time get any hail at all.
        GradientNoise noise = new GradientNoise(20261108L);
        int samples = 0;
        int hailing = 0;
        for (int cell = 0; cell < 40; cell++) {
            for (int tick = 0; tick < 200_000; tick += 400) {
                float surge = AtmosphereModel.hailSurge(noise.sample(cell * AtmosphereModel.HAIL_SURGE_PER_CELL, 0.37,
                        tick / AtmosphereModel.HAIL_SURGE_TICKS));
                samples++;
                if (AtmosphereModel.hailTarget(1.0F, 1.0F, 30.0F, surge) >= ClimateSimulation.HAIL_THRESHOLD) {
                    hailing++;
                }
            }
        }
        double share = (double) hailing / samples;
        assertTrue(share > 0.05 && share < 0.3, "hailing share " + share);
    }

    @Test
    void radiationFogNeedsExcessWaterStillAirAndAClearSky() {
        assertEquals(1.0F, AtmosphereModel.fog(1.3F, 0.5F, 0.1F, 0.0F), EPSILON);
        assertEquals(0.0F, AtmosphereModel.fog(1.0F, 0.5F, 0.1F, 0.0F), EPSILON, "no excess water");
        assertEquals(0.0F, AtmosphereModel.fog(1.3F, 8.0F, 0.1F, 0.0F), EPSILON, "wind mixes it away");
        assertEquals(0.0F, AtmosphereModel.fog(1.3F, 0.5F, 1.0F, 0.0F), EPSILON, "clouds keep the ground warm");
        assertEquals(0.0F, AtmosphereModel.fog(1.3F, 0.5F, 0.1F, 0.5F), EPSILON, "rain washes it out");
    }

    @Test
    void groundDriesFastInWarmWindyAirAndHardlyWhenFrozen() {
        float mild = AtmosphereModel.dryingTime(15.0F, 0.6F, 3.0F);
        assertTrue(AtmosphereModel.dryingTime(30.0F, 0.3F, 8.0F) < mild / 2.0F);
        assertTrue(AtmosphereModel.dryingTime(-5.0F, 0.9F, 0.0F) > AtmosphereModel.DRYING_TIME_TICKS * 2.0F);
        assertEquals(0.0F, AtmosphereModel.wetnessTarget(0.0F), EPSILON);
        assertEquals(1.0F, AtmosphereModel.wetnessTarget(0.75F), EPSILON);
    }

    @Test
    void precipitationTypeFollowsTemperature() {
        assertEquals(AtmosphereModel.PrecipitationType.SNOW, AtmosphereModel.precipitationType(-5.0F));
        assertEquals(AtmosphereModel.PrecipitationType.SNOW, AtmosphereModel.precipitationType(0.0F));
        assertEquals(AtmosphereModel.PrecipitationType.SLEET, AtmosphereModel.precipitationType(1.0F));
        assertEquals(AtmosphereModel.PrecipitationType.RAIN, AtmosphereModel.precipitationType(15.0F));
    }

    @Test
    void beaufortScaleMatchesReferenceSpeeds() {
        assertEquals(0.0F, AtmosphereModel.beaufort(0.0F), EPSILON);
        assertEquals(3.0F, AtmosphereModel.beaufort(4.4F), 0.1F);
        assertEquals(6.0F, AtmosphereModel.beaufort(12.3F), 0.1F);
        assertEquals(12.0F, AtmosphereModel.beaufort(60.0F), EPSILON);
    }

    @Test
    void windIsStrongerHigherUp() {
        assertEquals(10.0F, AtmosphereModel.windAtHeight(10.0F, 0.0F), EPSILON);
        assertEquals(15.0F, AtmosphereModel.windAtHeight(10.0F, 200.0F), EPSILON);
        assertEquals(10.0F, AtmosphereModel.windAtHeight(10.0F, Float.NaN), EPSILON);
    }
}
