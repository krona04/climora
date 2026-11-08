package net.krona.climora.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.krona.climora.weather.LocalWeather;
import net.krona.climora.weather.WeatherView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Beds work in a thunderstorm, as in vanilla, when the storm is over the bed. Vanilla decides it from the
 * darkness of a global storm, which never comes with local weather.
 * <p>
 * NeoForge moves this check into a lambda behind its {@code CanPlayerSleepEvent}, so there the hook finds
 * nothing ({@code require = 0}) and {@code ClimoraNeoForge} answers the event instead.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    @WrapOperation(method = "startSleepInBed", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isDay()Z"))
    private boolean climora$dayUnlessStormOverBed(Level level, Operation<Boolean> original) {
        return original.call(level) && !LocalWeather.isThunderingOver(level, ((ServerPlayer) (Object) this).blockPosition());
    }
}
