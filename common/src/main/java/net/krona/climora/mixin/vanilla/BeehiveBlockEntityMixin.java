package net.krona.climora.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.krona.climora.weather.LocalWeather;
import net.krona.climora.weather.WeatherView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A hive keeps its bees in while it rains over the hive. */
@Mixin(BeehiveBlockEntity.class)
abstract class BeehiveBlockEntityMixin {
    @WrapOperation(method = "releaseOccupant", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isRaining()Z"))
    private static boolean climora$rainingAroundHive(Level level, Operation<Boolean> original,
                                                     @Local(argsOnly = true, ordinal = 0) BlockPos hivePos) {
        WeatherView view = LocalWeather.view(level);
        return view == null ? original.call(level) : LocalWeather.isRainingAround(view, hivePos);
    }
}
