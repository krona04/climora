package net.krona.climora.mixin;

import net.krona.climora.weather.LocalWeather;
import net.krona.climora.weather.SkyWeather;
import net.krona.climora.weather.WeatherView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Local weather for every vanilla system that asks the level about rain: wet entities, fire,
 * farmland, sky darkness, fog and sounds.
 */
@Mixin(Level.class)
abstract class LevelMixin {
    @Inject(method = "isRainingAt", at = @At("HEAD"), cancellable = true)
    private void climora$localIsRainingAt(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        WeatherView view = LocalWeather.view(level);
        if (view != null) {
            cir.setReturnValue(LocalWeather.isRainingAt(level, view, pos));
        }
    }

    /** Client only: the server keeps the global rain level at 0 when local weather is on. */
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void climora$localRainLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (((Level) (Object) this).isClientSide()) {
            SkyWeather sky = LocalWeather.clientSky();
            if (sky != null) {
                cir.setReturnValue(sky.rainLevel(partialTick));
            }
        }
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void climora$localThunderLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (((Level) (Object) this).isClientSide()) {
            SkyWeather sky = LocalWeather.clientSky();
            if (sky != null) {
                cir.setReturnValue(sky.thunderLevel(partialTick));
            }
        }
    }
}
