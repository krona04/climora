package net.krona.climora.worldgen;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatitudeTest {
    private static final double HALF_WIDTH = 10000.0;
    // The vanilla temperature bands, as in the biome placement.
    private static final float FROZEN = -0.45F;
    private static final float COLD = -0.15F;
    private static final float TEMPERATE = 0.2F;
    private static final float WARM = 0.55F;
    private static final float TEMPERATURE_NOISE = 0.25F;

    @BeforeAll
    static void bootstrap() {
        // Density functions are registry entries.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void latitudeIsSymmetricAndStopsAtThePole() {
        assertEquals(0.0F, Latitude.of(0.0, HALF_WIDTH));
        assertEquals(0.5F, Latitude.of(5000.0, HALF_WIDTH), 1.0E-6F);
        assertEquals(0.5F, Latitude.of(-5000.0, HALF_WIDTH), 1.0E-6F);
        assertEquals(1.0F, Latitude.of(10000.0, HALF_WIDTH));
        assertEquals(1.0F, Latitude.of(-250000.0, HALF_WIDTH));
    }

    @Test
    void equatorIsWarmAndWet() {
        float temperature = Latitude.temperature(0.0F);
        assertTrue(temperature > TEMPERATE && temperature < WARM, "warm band: " + temperature);
        assertTrue(Latitude.humidity(0.0F) > 0.3F, "jungle humidity");
    }

    @Test
    void subtropicsAreHotAndDry() {
        assertTrue(Latitude.temperature(0.25F) > WARM);
        assertTrue(Latitude.humidity(0.25F) < -0.35F);
    }

    @Test
    void middleLatitudesAreTemperate() {
        float temperature = Latitude.temperature(0.45F);
        assertTrue(temperature > COLD && temperature < TEMPERATE, "temperate band: " + temperature);
    }

    @Test
    void borealBeltIsCold() {
        float temperature = Latitude.temperature(0.72F);
        assertTrue(temperature > FROZEN && temperature < COLD, "cold band: " + temperature);
    }

    @Test
    void poleAndBeyondStayFrozenWhateverTheNoise() {
        for (double z : new double[] {9500.0, 10000.0, 20000.0, -1.0E6}) {
            float temperature = Latitude.temperature(Latitude.of(z, HALF_WIDTH));
            assertTrue(temperature + TEMPERATURE_NOISE < FROZEN, "frozen at z=" + z + ": " + temperature);
        }
    }

    @Test
    void temperatureFallsSteadilyPastTheSubtropics() {
        float previous = Latitude.temperature(0.25F);
        for (float latitude = 0.26F; latitude <= 1.0F; latitude += 0.01F) {
            float temperature = Latitude.temperature(latitude);
            assertTrue(temperature <= previous + 1.0E-6F, "rises at " + latitude);
            previous = temperature;
        }
    }

    @Test
    void beltsAddTheirShareOfTheNoise() {
        LatitudeBelts belts = new LatitudeBelts(DensityFunctions.constant(0.8), LatitudeBelts.Parameter.TEMPERATURE,
                TEMPERATURE_NOISE, HALF_WIDTH);
        DensityFunction.SinglePointContext pole = new DensityFunction.SinglePointContext(0, 64, -12000);
        assertEquals(Latitude.temperature(1.0F) + TEMPERATURE_NOISE * 0.8, belts.compute(pole), 1.0E-6);
        assertTrue(belts.minValue() <= belts.compute(pole) && belts.compute(pole) <= belts.maxValue());
    }
}
