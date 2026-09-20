package net.krona.climora.climate;

import net.krona.climora.platform.PlatformClimate;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Climate of every vanilla Overworld biome: tropics, deserts, temperate lands, taiga, tundra,
 * mountains and oceans. Biomes from other mods fall back to their vanilla temperature and downfall
 * until tag-based profiles arrive in 0.5.0.
 */
public final class ClimateProfiles {
    private static final Map<ResourceKey<Biome>, ClimateProfile> PROFILES = new HashMap<>();

    static {
        // --- Tropics -------------------------------------------------------------------------
        put(Biomes.JUNGLE, 26.0F, 5.0F, 0.90F);
        put(Biomes.BAMBOO_JUNGLE, 26.0F, 5.0F, 0.90F);
        put(Biomes.SPARSE_JUNGLE, 26.5F, 7.0F, 0.75F);
        put(Biomes.MANGROVE_SWAMP, 25.0F, 5.0F, 0.90F);
        put(Biomes.SWAMP, 19.0F, 6.0F, 0.85F);
        put(Biomes.MUSHROOM_FIELDS, 16.0F, 5.0F, 0.80F);

        // --- Dry and hot ---------------------------------------------------------------------
        put(Biomes.DESERT, 30.0F, 16.0F, 0.05F);
        put(Biomes.BADLANDS, 28.0F, 15.0F, 0.08F);
        put(Biomes.ERODED_BADLANDS, 28.0F, 15.0F, 0.08F);
        put(Biomes.WOODED_BADLANDS, 26.0F, 13.0F, 0.20F);
        put(Biomes.SAVANNA, 27.0F, 12.0F, 0.30F);
        put(Biomes.SAVANNA_PLATEAU, 26.0F, 12.0F, 0.28F, 100);
        put(Biomes.WINDSWEPT_SAVANNA, 25.0F, 12.0F, 0.30F, 100);

        // --- Temperate -----------------------------------------------------------------------
        put(Biomes.PLAINS, 14.0F, 10.0F, 0.45F);
        put(Biomes.SUNFLOWER_PLAINS, 14.5F, 10.0F, 0.45F);
        put(Biomes.FOREST, 12.0F, 8.0F, 0.55F);
        put(Biomes.FLOWER_FOREST, 13.0F, 8.0F, 0.55F);
        put(Biomes.BIRCH_FOREST, 11.0F, 8.0F, 0.55F);
        put(Biomes.OLD_GROWTH_BIRCH_FOREST, 11.0F, 8.0F, 0.60F);
        put(Biomes.DARK_FOREST, 11.0F, 7.0F, 0.65F);
        put(Biomes.CHERRY_GROVE, 12.0F, 9.0F, 0.55F, 120);
        put(Biomes.MEADOW, 11.0F, 10.0F, 0.55F, 130);

        // --- Cool forests and taiga ----------------------------------------------------------
        put(Biomes.TAIGA, 5.0F, 10.0F, 0.60F);
        put(Biomes.OLD_GROWTH_PINE_TAIGA, 5.0F, 10.0F, 0.65F);
        put(Biomes.OLD_GROWTH_SPRUCE_TAIGA, 4.0F, 10.0F, 0.65F);
        put(Biomes.WINDSWEPT_FOREST, 7.0F, 9.0F, 0.55F, 110);
        put(Biomes.WINDSWEPT_HILLS, 7.0F, 10.0F, 0.50F, 110);
        put(Biomes.WINDSWEPT_GRAVELLY_HILLS, 6.0F, 10.0F, 0.45F, 110);

        // --- Tundra and ice ------------------------------------------------------------------
        put(Biomes.SNOWY_TAIGA, -5.0F, 9.0F, 0.60F);
        put(Biomes.SNOWY_PLAINS, -8.0F, 10.0F, 0.50F);
        put(Biomes.ICE_SPIKES, -12.0F, 10.0F, 0.35F);
        put(Biomes.SNOWY_BEACH, -3.0F, 7.0F, 0.65F);
        put(Biomes.FROZEN_RIVER, -7.0F, 8.0F, 0.70F);

        // --- Mountains (temperature given at mountain height) --------------------------------
        put(Biomes.GROVE, -1.0F, 8.0F, 0.65F, 150);
        put(Biomes.SNOWY_SLOPES, -6.0F, 8.0F, 0.60F, 170);
        put(Biomes.JAGGED_PEAKS, -12.0F, 7.0F, 0.50F, 220);
        put(Biomes.FROZEN_PEAKS, -14.0F, 7.0F, 0.55F, 220);
        put(Biomes.STONY_PEAKS, 6.0F, 9.0F, 0.45F, 180);

        // --- Shores and rivers ---------------------------------------------------------------
        put(Biomes.BEACH, 18.0F, 7.0F, 0.70F);
        put(Biomes.STONY_SHORE, 9.0F, 8.0F, 0.65F);
        put(Biomes.RIVER, 13.0F, 7.0F, 0.75F);

        // --- Oceans: the sea barely changes temperature between day and night ----------------
        put(Biomes.WARM_OCEAN, 27.0F, 2.0F, 0.85F);
        put(Biomes.LUKEWARM_OCEAN, 22.0F, 2.0F, 0.85F);
        put(Biomes.DEEP_LUKEWARM_OCEAN, 21.0F, 2.0F, 0.85F);
        put(Biomes.OCEAN, 15.0F, 2.5F, 0.80F);
        put(Biomes.DEEP_OCEAN, 14.0F, 2.5F, 0.80F);
        put(Biomes.COLD_OCEAN, 7.0F, 2.5F, 0.75F);
        put(Biomes.DEEP_COLD_OCEAN, 6.0F, 2.5F, 0.75F);
        put(Biomes.FROZEN_OCEAN, -4.0F, 3.0F, 0.65F);
        put(Biomes.DEEP_FROZEN_OCEAN, -4.0F, 3.0F, 0.65F);

        // --- Caves: sampled only when a cave biome reaches the surface -----------------------
        put(Biomes.LUSH_CAVES, 18.0F, 3.0F, 0.90F);
        put(Biomes.DRIPSTONE_CAVES, 13.0F, 3.0F, 0.60F);
        put(Biomes.DEEP_DARK, 10.0F, 2.0F, 0.55F);
    }

    private ClimateProfiles() {
    }

    private static void put(ResourceKey<Biome> biome, float meanTemperature, float amplitude, float humidity) {
        PROFILES.put(biome, new ClimateProfile(meanTemperature, amplitude, humidity));
    }

    private static void put(ResourceKey<Biome> biome, float meanTemperature, float amplitude, float humidity, int elevation) {
        PROFILES.put(biome, new ClimateProfile(meanTemperature, amplitude, humidity, elevation));
    }

    /** Profile of a biome, or one derived from its vanilla temperature and downfall. */
    public static ClimateProfile of(Holder<Biome> biome) {
        ClimateProfile profile = biome.unwrapKey().map(PROFILES::get).orElse(null);
        return profile != null ? profile : fallback(biome.value().getBaseTemperature(), PlatformClimate.biomeDownfall(biome.value()));
    }

    /** Only for biomes without a profile: the rough mapping used before 0.1.0. */
    public static ClimateProfile fallback(float vanillaTemperature, float downfall) {
        float humidity = Math.max(0.0F, Math.min(1.0F, downfall));
        return new ClimateProfile(
                ClimateModel.biomeToCelsius(vanillaTemperature),
                ClimateModel.diurnalAmplitude(humidity),
                humidity);
    }

    /** Profile of a vanilla biome, or null if it has none (the Nether, the End and modded biomes). */
    @Nullable
    public static ClimateProfile get(ResourceKey<Biome> biome) {
        return PROFILES.get(biome);
    }

    public static int count() {
        return PROFILES.size();
    }
}
