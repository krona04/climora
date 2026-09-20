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
