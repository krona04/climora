package net.krona.climora.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.krona.climora.weather.LocalWeather;
import net.krona.climora.weather.WeatherView;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Monsters spawn in the gloom of a thunderstorm where the storm is, as they do in vanilla thunder. */
@Mixin(Monster.class)
abstract class MonsterMixin {
    @WrapOperation(method = "isDarkEnoughToSpawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isThundering()Z"))
    private static boolean climora$thunderAroundSpawn(ServerLevel level, Operation<Boolean> original,
                                                      @Local(argsOnly = true) BlockPos spawnPos) {
        WeatherView view = LocalWeather.view(level);
        return view == null ? original.call(level) : LocalWeather.isThunderingAround(view, spawnPos);
    }
}
