package net.krona.climora.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.krona.climora.client.ClientWeather;
import net.krona.climora.client.WeatherFog;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Weather fog: closer fog in radiation fog, downpours and heavy snow, and a grey fog colour in radiation fog.
 * Both hooks run last, after vanilla and after the fog events of NeoForge.
 */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    /** The horizon greys out under a closed cloud deck too. */
    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private static float climora$overcastFog(ClientLevel level, float partialTick, Operation<Float> original) {
        return ClientWeather.INSTANCE.skyGreyness(original.call(level, partialTick), partialTick);
    }

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void climora$weatherFog(Camera camera, FogRenderer.FogMode mode, float farPlaneDistance,
                                           boolean thickFog, float partialTick, CallbackInfo ci) {
        WeatherFog.apply(camera, mode);
    }

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void climora$fogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                         float darkenWorld, CallbackInfo ci) {
        float[] color = WeatherFog.tint(camera, fogRed, fogGreen, fogBlue);
        if (color[0] != fogRed || color[1] != fogGreen || color[2] != fogBlue) {
            fogRed = color[0];
            fogGreen = color[1];
            fogBlue = color[2];
            RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
        }
    }
}
