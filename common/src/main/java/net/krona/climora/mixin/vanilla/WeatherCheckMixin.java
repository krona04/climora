package net.krona.climora.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.krona.climora.weather.LocalWeather;
import net.krona.climora.weather.WeatherView;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.WeatherCheck;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The {@code weather_check} loot condition of data packs asks about the weather where the loot drops. */
@Mixin(WeatherCheck.class)
abstract class WeatherCheckMixin {
    @WrapOperation(method = "test(Lnet/minecraft/world/level/storage/loot/LootContext;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
    private boolean climora$rainingAtLoot(ServerLevel level, Operation<Boolean> original, @Local(argsOnly = true) LootContext context) {
        WeatherView view = LocalWeather.view(level);
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        return view == null || origin == null ? original.call(level) : LocalWeather.isRainingAround(view, BlockPos.containing(origin));
    }

    @WrapOperation(method = "test(Lnet/minecraft/world/level/storage/loot/LootContext;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isThundering()Z"))
    private boolean climora$thunderAtLoot(ServerLevel level, Operation<Boolean> original, @Local(argsOnly = true) LootContext context) {
        WeatherView view = LocalWeather.view(level);
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        return view == null || origin == null ? original.call(level) : LocalWeather.isThunderingAround(view, BlockPos.containing(origin));
    }
}
