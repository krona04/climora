package net.krona.climora.client.compat;

import pigcart.particlerain.config.ConfigData;
import pigcart.particlerain.config.ConfigManager;

/** Particle Rain's own options. Names Particle Rain's classes: only call when it is loaded. */
final class ParticleRainConfig {
    private ParticleRainConfig() {
    }

    static boolean renderDefaultRain() {
        return compat().renderDefaultRain;
    }

    static boolean renderDefaultSnow() {
        return compat().renderDefaultSnow;
    }

    static boolean defaultSplashing() {
        return compat().doDefaultSplashing;
    }

    static float rainVolume() {
        return ConfigManager.getConfig().sound.rainVolume;
    }

    private static ConfigData.CompatibilityOptions compat() {
        return ConfigManager.getConfig().compat;
    }
}
