package net.krona.climora.climate;

import net.krona.climora.weather.LocalWeather;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Snowy biomes stay snowy: they used to thaw every day at noon. */
class SnowCoverTest {
    private static final List<ResourceKey<Biome>> SNOW_COVERED = List.of(
            Biomes.SNOWY_PLAINS, Biomes.ICE_SPIKES, Biomes.SNOWY_TAIGA, Biomes.SNOWY_BEACH, Biomes.FROZEN_RIVER,
            Biomes.GROVE, Biomes.SNOWY_SLOPES, Biomes.JAGGED_PEAKS, Biomes.FROZEN_PEAKS);

    @Test
    void snowCoveredBiomesStayBelowMeltingAllDay() {
        for (ResourceKey<Biome> biome : SNOW_COVERED) {
            ClimateProfile profile = ClimateProfiles.get(biome);
            assertNotNull(profile, biome.location().toString());
            float warmest = profile.meanTemperature() + profile.diurnalAmplitude();
            assertTrue(warmest < LocalWeather.SNOW_MELT_TEMPERATURE - 0.9F,
                    biome.location() + " reaches " + warmest + " °C at noon");
        }
    }

    @Test
    void frozenOceanIceHolds() {
        ClimateProfile ocean = ClimateProfiles.get(Biomes.FROZEN_OCEAN);
        assertNotNull(ocean);
        assertTrue(ocean.meanTemperature() + ocean.diurnalAmplitude() < LocalWeather.ICE_MELT_TEMPERATURE);
    }

    @Test
    void aSnowfieldInsideAMildCellStaysFrozenAtNoon() {
        // A cell half snowy plains, half plains: its average climate is mild.
        ClimateProfile snow = ClimateProfiles.get(Biomes.SNOWY_PLAINS);
        ClimateProfile plains = ClimateProfiles.get(Biomes.PLAINS);
        assertNotNull(snow);
        assertNotNull(plains);
        float cellBase = (snow.meanTemperature() + plains.meanTemperature()) / 2.0F;
        float cellAmplitude = (snow.diurnalAmplitude() + plains.diurnalAmplitude()) / 2.0F;
        float noon = 1.0F;
        float cellAtNoon = cellBase + cellAmplitude * noon;
        assertTrue(cellAtNoon > LocalWeather.SNOW_MELT_TEMPERATURE, "the cell average alone would melt the snow");

        float onSnow = cellAtNoon + Microclimate.offset(snow, cellBase, cellAmplitude, ClimateProfile.SEA_LEVEL, noon);
        assertTrue(onSnow < LocalWeather.SNOW_MELT_TEMPERATURE, "snowfield at noon: " + onSnow + " °C");
        float onGrass = cellAtNoon + Microclimate.offset(plains, cellBase, cellAmplitude, ClimateProfile.SEA_LEVEL, noon);
        assertTrue(onGrass > cellAtNoon, "the meadow next to it is warmer than the average");
    }
}
