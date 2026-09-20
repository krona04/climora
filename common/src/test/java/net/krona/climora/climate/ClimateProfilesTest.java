package net.krona.climora.climate;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClimateProfilesTest {

    /** Every Overworld biome a player can stand in must have a profile. */
    private static final List<ResourceKey<Biome>> OVERWORLD_BIOMES = List.of(
            Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.SNOWY_PLAINS, Biomes.ICE_SPIKES, Biomes.DESERT,
            Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST,
            Biomes.DARK_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.OLD_GROWTH_PINE_TAIGA,
            Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.TAIGA, Biomes.SNOWY_TAIGA, Biomes.SAVANNA,
            Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS,
            Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_SAVANNA, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE,
            Biomes.BAMBOO_JUNGLE, Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS,
            Biomes.MEADOW, Biomes.CHERRY_GROVE, Biomes.GROVE, Biomes.SNOWY_SLOPES, Biomes.FROZEN_PEAKS,
            Biomes.JAGGED_PEAKS, Biomes.STONY_PEAKS, Biomes.RIVER, Biomes.FROZEN_RIVER, Biomes.BEACH,
            Biomes.SNOWY_BEACH, Biomes.STONY_SHORE, Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN,
            Biomes.DEEP_LUKEWARM_OCEAN, Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.COLD_OCEAN,
            Biomes.DEEP_COLD_OCEAN, Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.MUSHROOM_FIELDS,
            Biomes.LUSH_CAVES, Biomes.DRIPSTONE_CAVES, Biomes.DEEP_DARK);

    @Test
    void everyOverworldBiomeHasAProfileWithSaneValues() {
        for (ResourceKey<Biome> biome : OVERWORLD_BIOMES) {
            ClimateProfile profile = ClimateProfiles.get(biome);
            assertNotNull(profile, "no profile for " + biome.location());
            assertTrue(profile.meanTemperature() > -30.0F && profile.meanTemperature() < 45.0F,
                    biome.location() + " temperature " + profile.meanTemperature());
            assertTrue(profile.diurnalAmplitude() >= 1.0F && profile.diurnalAmplitude() <= 20.0F,
                    biome.location() + " amplitude " + profile.diurnalAmplitude());
            assertTrue(profile.humidity() >= 0.0F && profile.humidity() <= 1.0F,
                    biome.location() + " humidity " + profile.humidity());
            assertTrue(profile.referenceElevation() >= ClimateProfile.SEA_LEVEL
                            && profile.referenceElevation() <= 320,
                    biome.location() + " reference elevation " + profile.referenceElevation());
        }
    }

    @Test
    void climateZonesAreOrderedFromTropicsToTundra() {
        float jungle = temperature(Biomes.JUNGLE);
        float savanna = temperature(Biomes.SAVANNA);
        float plains = temperature(Biomes.PLAINS);
        float taiga = temperature(Biomes.TAIGA);
        float snowyPlains = temperature(Biomes.SNOWY_PLAINS);
        float iceSpikes = temperature(Biomes.ICE_SPIKES);

        assertTrue(savanna > jungle - 2.0F, "savanna is hot too");
        assertTrue(jungle > plains, "jungle warmer than plains");
        assertTrue(plains > taiga, "plains warmer than taiga");
        assertTrue(taiga > snowyPlains, "taiga warmer than tundra");
        assertTrue(snowyPlains > iceSpikes, "ice spikes are the coldest flat biome");
        assertTrue(snowyPlains < 0.0F, "tundra is below freezing on average");
    }

    @Test
    void desertsAreHotAndDryWithLargeSwings() {
        ClimateProfile desert = ClimateProfiles.get(Biomes.DESERT);
        ClimateProfile jungle = ClimateProfiles.get(Biomes.JUNGLE);
        assertNotNull(desert);
        assertNotNull(jungle);

        assertTrue(desert.humidity() < 0.15F, "deserts are dry");
        assertTrue(jungle.humidity() > 0.8F, "jungles are humid");
        assertTrue(desert.diurnalAmplitude() > 2.0F * jungle.diurnalAmplitude(), "deserts swing more");
    }

    @Test
    void oceansAreHumidAndSteady() {
        for (ResourceKey<Biome> ocean : List.of(Biomes.OCEAN, Biomes.WARM_OCEAN, Biomes.COLD_OCEAN)) {
            ClimateProfile profile = ClimateProfiles.get(ocean);
            assertNotNull(profile);
            assertTrue(profile.humidity() >= 0.6F, ocean.location() + " is humid");
            assertTrue(profile.diurnalAmplitude() <= 3.0F, ocean.location() + " barely changes day to night");
        }
        assertTrue(temperature(Biomes.WARM_OCEAN) > temperature(Biomes.OCEAN));
        assertTrue(temperature(Biomes.OCEAN) > temperature(Biomes.FROZEN_OCEAN));
    }

    @Test
    void mountainTemperaturesAreGivenAtMountainHeight() {
        ClimateProfile peaks = ClimateProfiles.get(Biomes.FROZEN_PEAKS);
        assertNotNull(peaks);
        assertTrue(peaks.referenceElevation() > 150, "peaks are high");

        // At their own height the peaks are freezing, and they stay colder than the valley below.
        assertEquals(peaks.meanTemperature(), peaks.meanTemperatureAt(peaks.referenceElevation()), 1.0E-3F);
        assertTrue(peaks.meanTemperatureAt(300) < peaks.meanTemperature(), "colder higher up");
        assertTrue(peaks.meanTemperatureAt(ClimateProfile.SEA_LEVEL) > peaks.meanTemperature(), "warmer at sea level");
        assertTrue(peaks.meanTemperatureAt(300) < -14.0F);
    }

    @Test
    void unknownBiomesFallBackToVanillaValues() {
        ClimateProfile plainsLike = ClimateProfiles.fallback(0.8F, 0.4F);
        assertEquals(ClimateModel.biomeToCelsius(0.8F), plainsLike.meanTemperature(), 1.0E-3F);
        assertEquals(0.4F, plainsLike.humidity(), 1.0E-3F);
        assertEquals(ClimateProfile.SEA_LEVEL, plainsLike.referenceElevation());

        ClimateProfile dry = ClimateProfiles.fallback(2.0F, 0.0F);
        assertTrue(dry.diurnalAmplitude() > plainsLike.diurnalAmplitude(), "dry biomes swing more");
    }

    private static float temperature(ResourceKey<Biome> biome) {
        ClimateProfile profile = ClimateProfiles.get(biome);
        assertNotNull(profile, "no profile for " + biome.location());
        return profile.meanTemperature();
    }
}
