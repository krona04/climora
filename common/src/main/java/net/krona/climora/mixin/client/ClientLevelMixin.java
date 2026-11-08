package net.krona.climora.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.krona.climora.client.ClientWeather;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A closed cloud deck greys the sky and the clouds and dims the day, as rain does in vanilla. */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @WrapOperation(method = {"getSkyColor", "getCloudColor", "getSkyDarken"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private float climora$overcastSky(ClientLevel level, float partialTick, Operation<Float> original) {
        return ClientWeather.INSTANCE.skyGreyness(original.call(level, partialTick), partialTick);
    }
}
