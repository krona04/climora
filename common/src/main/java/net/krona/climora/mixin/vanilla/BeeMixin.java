package net.krona.climora.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.krona.climora.weather.LocalWeather;
import net.krona.climora.weather.WeatherView;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Bees head home when it rains where they are, not when it rains somewhere in the world. */
@Mixin(Bee.class)
abstract class BeeMixin {
    @WrapOperation(method = "wantsToEnterHive", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isRaining()Z"))
    private boolean climora$rainingAroundBee(Level level, Operation<Boolean> original) {
        WeatherView view = LocalWeather.view(level);
        return view == null ? original.call(level) : LocalWeather.isRainingAround(view, ((Bee) (Object) this).blockPosition());
    }
}
