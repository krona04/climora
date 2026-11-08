package net.krona.climora.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.krona.climora.weather.LocalWeather;
import net.krona.climora.weather.WeatherView;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Rain puts out fires and keeps them from spreading. Vanilla first asks whether it rains anywhere in the
 * world and only then checks the blocks around the fire, so with local weather the global answer was
 * always "no" and fires burned through any downpour. The block check after it is already local.
 */
@Mixin(FireBlock.class)
abstract class FireBlockMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
    private boolean climora$rainingAroundFire(ServerLevel level, Operation<Boolean> original,
                                              @Local(argsOnly = true) BlockPos firePos) {
        WeatherView view = LocalWeather.view(level);
        return view == null ? original.call(level) : LocalWeather.isRainingAround(view, firePos);
    }
}
