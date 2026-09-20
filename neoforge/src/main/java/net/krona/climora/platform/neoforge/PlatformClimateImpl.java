package net.krona.climora.platform.neoforge;

import net.minecraft.world.level.biome.Biome;

public final class PlatformClimateImpl {
    private PlatformClimateImpl() {
    }

    /** NeoForge keeps climateSettings private and applies biome modifiers through this getter. */
    public static float biomeDownfall(Biome biome) {
        return biome.getModifiedClimateSettings().downfall();
    }
}
