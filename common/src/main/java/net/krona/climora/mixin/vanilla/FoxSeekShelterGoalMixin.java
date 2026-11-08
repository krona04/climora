package net.krona.climora.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.krona.climora.weather.LocalWeather;
import net.krona.climora.weather.WeatherView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Foxes run for shelter from a thunderstorm that is over them.
 * <p>
 * Vanilla asks {@code isThundering() && canSeeSky(foxPos)}. The goal reaches its fox only through the
 * synthetic outer-class field, but the fox position is the argument of that first {@code canSeeSky}, so
 * the thunder check moves there: the global check lets the call through and the sky check asks for local
 * thunder at the same position. The later daytime sky check is left alone.
 */
@Mixin(targets = "net.minecraft.world.entity.animal.Fox$SeekShelterGoal")
abstract class FoxSeekShelterGoalMixin {
    @WrapOperation(method = "canUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isThundering()Z"))
    private boolean climora$askAtTheFox(Level level, Operation<Boolean> original) {
        return LocalWeather.view(level) != null || original.call(level);
    }

    @WrapOperation(method = "canUse", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/level/Level;canSeeSky(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean climora$thunderAroundFox(Level level, BlockPos pos, Operation<Boolean> original) {
        WeatherView view = LocalWeather.view(level);
        return original.call(level, pos) && (view == null || LocalWeather.isThunderingAround(view, pos));
    }
}
