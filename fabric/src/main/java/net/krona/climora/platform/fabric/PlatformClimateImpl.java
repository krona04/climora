package net.krona.climora.platform.fabric;

import net.minecraft.world.level.biome.Biome;

public final class PlatformClimateImpl {
    private PlatformClimateImpl() {
    }

    /** Opened by climora.accesswidener. Fabric biome modifications replace this field in place. */
    public static float biomeDownfall(Biome biome) {
        return biome.climateSettings.downfall();
    }
}
