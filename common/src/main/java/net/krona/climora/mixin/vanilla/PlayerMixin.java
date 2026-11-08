package net.krona.climora.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.krona.climora.weather.LocalWeather;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A player asleep through a daytime thunderstorm is not woken up while the storm is still overhead. */
@Mixin(Player.class)
abstract class PlayerMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isDay()Z"))
    private boolean climora$dayUnlessStormOverBed(Level level, Operation<Boolean> original) {
        return original.call(level) && !LocalWeather.isThunderingOver(level, ((Player) (Object) this).blockPosition());
    }
}
