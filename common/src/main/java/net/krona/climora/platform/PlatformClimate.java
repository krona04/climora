package net.krona.climora.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.level.biome.Biome;

/**
 * Climate data that each loader exposes differently. Implemented in {@code platform.<loader>.PlatformClimateImpl}.
 */
public final class PlatformClimate {
    private PlatformClimate() {
    }

    /** Biome downfall, 0..1, including changes made by biome modifiers. */
    @ExpectPlatform
    public static float biomeDownfall(Biome biome) {
        throw new AssertionError();
    }
}
