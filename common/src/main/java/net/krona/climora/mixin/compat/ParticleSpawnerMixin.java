package net.krona.climora.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.krona.climora.client.compat.ParticleRainWeather;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pigcart.particlerain.config.ParticleData;

/**
 * Particle Rain's spawner follows the local weather ({@link ParticleRainWeather}). Applies only when Particle
 * Rain is installed. Its methods are matched by name alone: Particle Rain's jars carry Minecraft's names of
 * each loader, which a descriptor here could not match on both. {@code require = 0}, so a changed Particle
 * Rain falls back to its own behaviour instead of a crash.
 */
@Pseudo
@Mixin(targets = "pigcart.particlerain.ParticleSpawner", remap = false)
abstract class ParticleSpawnerMixin {
    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private static void climora$followLocalRain(ClientLevel level, Vec3 cameraPos, CallbackInfo ci) {
        ParticleRainWeather.tick(level);
    }

    @WrapOperation(method = {"tickSkyFX", "tickSurfaceFX", "tickBlockFX"},
            at = @At(value = "INVOKE", target = "Lpigcart/particlerain/VersionUtil;getPrecipitationAt"), require = 0)
    private static Biome.Precipitation climora$localPrecipitation(Level level, Holder<Biome> biome, BlockPos pos,
                                                                  Operation<Biome.Precipitation> original) {
        return ParticleRainWeather.precipitationAt(pos, original.call(level, biome, pos));
    }

    @WrapOperation(method = {"tickSkyFX", "tickSurfaceFX", "tickBlockFX"},
            at = @At(value = "INVOKE", target = "Lpigcart/particlerain/config/ParticleData$Weather;isCurrent"), require = 0)
    private static Boolean climora$localWeatherNearby(ParticleData.Weather weather, ClientLevel level, Operation<Boolean> original) {
        return ParticleRainWeather.isCurrent(weather, level, original.call(weather, level));
    }
}
