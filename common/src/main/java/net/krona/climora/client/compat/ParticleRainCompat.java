package net.krona.climora.client.compat;

import dev.architectury.platform.Platform;

/**
 * What Climora leaves to <a href="https://modrinth.com/mod/particle-rain">Particle Rain</a> when it is installed.
 * <p>
 * Particle Rain draws rain and snow as particles. Its mixins into the vanilla weather code never run while
 * Climora draws local weather, so it is fed through hooks into its own spawner instead
 * ({@code mixin.compat}, {@link ParticleRainWeather}): it gets the local weather, and Climora stops drawing
 * what Particle Rain now draws, so nothing falls twice. Hail and the distant curtains stay Climora's: Particle
 * Rain has neither.
 * <p>
 * This class is safe to load without Particle Rain; everything that names its classes is in
 * {@link ParticleRainConfig} and {@link ParticleRainWeather}, which are only touched when it is loaded.
 */
public final class ParticleRainCompat {
    private static final boolean LOADED = Platform.isModLoaded("particlerain");
    /** Vanilla's rain volume, which Particle Rain's rain volume slider replaces. */
    private static final float VANILLA_RAIN_VOLUME = 0.2F;

    private ParticleRainCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    /** Particle Rain draws the falling rain, unless its "render default rain" option asks for streaks too. */
    public static boolean drawsRain() {
        return LOADED && !ParticleRainConfig.renderDefaultRain();
    }

    /** The same for snow. */
    public static boolean drawsSnow() {
        return LOADED && !ParticleRainConfig.renderDefaultSnow();
    }

    /** Particle Rain makes its own ripples and splashes and turns the vanilla ones off unless asked not to. */
    public static boolean replacesSplashes() {
        return LOADED && !ParticleRainConfig.defaultSplashing();
    }

    /** Factor on the rain sound, so Particle Rain's rain volume slider still works. */
    public static float rainVolume() {
        return LOADED ? ParticleRainConfig.rainVolume() / VANILLA_RAIN_VOLUME : 1.0F;
    }
}
