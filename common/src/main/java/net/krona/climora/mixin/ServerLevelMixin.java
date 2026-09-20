package net.krona.climora.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.krona.climora.weather.LocalWeather;
import net.krona.climora.weather.WeatherView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns off the global vanilla weather cycle and makes snow and rain on blocks follow local weather.
 * Vanilla thunder needs a global thunderstorm, so it never happens; {@code LightningSpawner} replaces it.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void climora$disableGlobalWeather(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (LocalWeather.view(level) == null) {
            return;
        }
        ci.cancel();
        if (level.getRainLevel(1.0F) > 0.0F || level.getThunderLevel(1.0F) > 0.0F) {
            // A world saved during vanilla rain: end it once for everyone.
            level.resetWeatherCycle();
            level.setRainLevel(0.0F);
            level.setThunderLevel(0.0F);
            level.getServer().getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0F), level.dimension());
            level.getServer().getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0.0F), level.dimension());
            level.getServer().getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0.0F), level.dimension());
        }
    }

    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
    private boolean climora$localPrecipitation(ServerLevel level, Operation<Boolean> original, @Local(argsOnly = true) BlockPos pos) {
        WeatherView view = LocalWeather.view(level);
        if (view == null) {
            return original.call(level);
        }
        return view.precipitation(pos.getX() + 0.5, pos.getZ() + 0.5) >= LocalWeather.PRECIPITATION_THRESHOLD;
    }

    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean climora$localFreezing(Biome biome, LevelReader reader, BlockPos pos, Operation<Boolean> original) {
        ServerLevel level = (ServerLevel) (Object) this;
        WeatherView view = LocalWeather.view(level);
        if (view == null) {
            return original.call(biome, reader, pos);
        }
        return LocalWeather.shouldFreeze(level, view, pos);
    }

    /** Warm air melts snow and ice, even in the dark. Vanilla only melts them in bright light. */
    @Inject(method = "tickPrecipitation", at = @At("TAIL"))
    private void climora$thaw(BlockPos pos, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        WeatherView view = LocalWeather.view(level);
        if (view != null) {
            LocalWeather.tickThaw(level, view, pos);
        }
    }

    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean climora$localSnow(Biome biome, LevelReader reader, BlockPos pos, Operation<Boolean> original) {
        ServerLevel level = (ServerLevel) (Object) this;
        WeatherView view = LocalWeather.view(level);
        if (view == null) {
            return original.call(biome, reader, pos);
        }
        return LocalWeather.shouldSnow(level, view, pos);
    }

    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation climora$localPrecipitationType(Biome biome, BlockPos pos, Operation<Biome.Precipitation> original) {
        WeatherView view = LocalWeather.view((ServerLevel) (Object) this);
        if (view == null) {
            return original.call(biome, pos);
        }
        return LocalWeather.vanillaPrecipitationAt(view, pos);
    }
}
