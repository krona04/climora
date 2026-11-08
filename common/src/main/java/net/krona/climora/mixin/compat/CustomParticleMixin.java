package net.krona.climora.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.krona.climora.client.compat.ParticleRainWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import pigcart.particlerain.particle.CustomParticle;

/** A falling Particle Rain particle checks the local weather, not the biome (see {@link ParticleSpawnerMixin}). */
@Pseudo
@Mixin(targets = "pigcart.particlerain.particle.CustomParticle", remap = false)
abstract class CustomParticleMixin {
    @WrapOperation(method = "onPositionUpdate",
            at = @At(value = "INVOKE", target = "Lpigcart/particlerain/VersionUtil;getPrecipitationAt"), require = 0)
    private Biome.Precipitation climora$localPrecipitation(Level level, Holder<Biome> biome, BlockPos pos,
                                                           Operation<Biome.Precipitation> original) {
        CustomParticle particle = (CustomParticle) (Object) this;
        return ParticleRainWeather.underParticle(pos, particle.data.precipitation, original.call(level, biome, pos));
    }
}
