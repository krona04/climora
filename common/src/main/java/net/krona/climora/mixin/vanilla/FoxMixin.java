package net.krona.climora.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.krona.climora.weather.LocalWeather;
import net.krona.climora.weather.WeatherView;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A thunderstorm over a sleeping fox wakes it up. */
@Mixin(Fox.class)
abstract class FoxMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isThundering()Z"))
    private boolean climora$thunderAroundFox(Level level, Operation<Boolean> original) {
        WeatherView view = LocalWeather.view(level);
        return view == null ? original.call(level) : LocalWeather.isThunderingAround(view, ((Fox) (Object) this).blockPosition());
    }
}
