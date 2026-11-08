package net.krona.climora.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.krona.climora.weather.LocalWeather;
import net.krona.climora.weather.WeatherView;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Bees do not start or keep pollinating in local rain.
 * <p>
 * The goal reaches its bee only through the synthetic outer-class field, whose name differs between
 * mappings. So instead of the rain check itself, this wraps the bee call right before it: in
 * {@code canBeeUse} both "has nectar" and "it rains" end the check with false, and in
 * {@code canBeeContinueToUse} both "no flower" and "it rains" do. Answering them for local rain gives
 * exactly the vanilla behaviour. The global rain check after them stays and is simply never true.
 */
@Mixin(targets = "net.minecraft.world.entity.animal.Bee$BeePollinateGoal")
abstract class BeePollinateGoalMixin {
    @WrapOperation(method = "canBeeUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Bee;hasNectar()Z"))
    private boolean climora$noStartInRain(Bee bee, Operation<Boolean> original) {
        return original.call(bee) || rainingAround(bee);
    }

    @WrapOperation(method = "canBeeContinueToUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Bee;hasSavedFlowerPos()Z"))
    private boolean climora$stopInRain(Bee bee, Operation<Boolean> original) {
        return original.call(bee) && !rainingAround(bee);
    }

    private static boolean rainingAround(Bee bee) {
        WeatherView view = LocalWeather.view(bee.level());
        return view != null && LocalWeather.isRainingAround(view, bee.blockPosition());
    }
}
