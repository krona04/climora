package net.krona.climora.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.krona.climora.registry.ClimoraRegistries;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

/**
 * Sound Physics Remastered leaves the vanilla rain and thunder sounds alone by default: they stand for weather
 * all around the listener, not for a sound at one block, and are played so often that tracing each one costs
 * a lot. Climora's hail sound is the same kind of sound, so it gets the same default. Its own settings file
 * still decides, so a player can turn sound physics for hail back on there.
 */
@Pseudo
@Mixin(targets = "com.sonicether.soundphysics.config.SoundRateConfig", remap = false)
abstract class SoundRateConfigMixin {
    @ModifyReturnValue(method = "createDefaultMap", at = @At("RETURN"), require = 0)
    private Map<ResourceLocation, Integer> climora$weatherSoundsLikeVanilla(Map<ResourceLocation, Integer> defaults) {
        // 0: no sound physics for this sound, as for minecraft:weather.rain.
        defaults.put(ClimoraRegistries.HAIL_SOUND.getId(), 0);
        return defaults;
    }
}
