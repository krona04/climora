package net.krona.climora.client.compat;

import dev.architectury.platform.Platform;
import net.krona.climora.config.ClimoraConfig;

/**
 * Weather sounds with <a href="https://modrinth.com/mod/sound-physics-remastered">Sound Physics Remastered</a>.
 * <p>
 * That mod echoes sounds and muffles them behind walls, but leaves the vanilla rain and thunder alone by default.
 * With {@code weatherSoundPhysics} on, Climora plays rain and hail as its own sound events, which it does
 * process: in a house, rain comes in through the door and the windows, in a cave it echoes. Climora then drops
 * its own "under a roof" muffling, so nothing is muffled twice. With the option off, rain is vanilla rain and
 * hail the plain {@code climora:weather.hail}, which that mod skips by default ({@code SoundRateConfigMixin}).
 */
public final class SoundPhysicsCompat {
    private static final boolean LOADED = Platform.isModLoaded("sound_physics_remastered");

    private SoundPhysicsCompat() {
    }

    /** Rain and hail are left to Sound Physics Remastered: muffled by walls and echoing like any other sound. */
    public static boolean spatialWeather() {
        return LOADED && ClimoraConfig.client().weatherSoundPhysics;
    }
}
