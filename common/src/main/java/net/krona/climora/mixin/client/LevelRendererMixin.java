package net.krona.climora.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.krona.climora.client.ClientWeather;
import net.krona.climora.client.CloudRenderer;
import net.krona.climora.client.PrecipitationRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the global vanilla rain, snow and clouds with local weather while the server sends it.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private int ticks;

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void climora$renderLocalPrecipitation(LightTexture lightTexture, float partialTick,
                                                     double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        if (PrecipitationRenderer.replacesVanilla()) {
            ci.cancel();
            PrecipitationRenderer.render(minecraft, lightTexture, ticks, partialTick, cameraX, cameraY, cameraZ);
        }
    }

    @Inject(method = "tickRain", at = @At("HEAD"), cancellable = true)
    private void climora$tickLocalPrecipitation(Camera camera, CallbackInfo ci) {
        if (PrecipitationRenderer.replacesVanilla()) {
            ci.cancel();
            PrecipitationRenderer.tick(minecraft, camera, ticks);
        }
    }

    /** The sun, moon and stars fade behind a closed cloud deck, as they do in rain. */
    @WrapOperation(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private float climora$overcastHidesTheSun(ClientLevel level, float partialTick, Operation<Float> original) {
        return ClientWeather.INSTANCE.skyGreyness(original.call(level, partialTick), partialTick);
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void climora$renderDynamicClouds(PoseStack poseStack, Matrix4f modelView, Matrix4f projection, float partialTick,
                                               double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        if (CloudRenderer.replacesVanilla()) {
            ci.cancel();
            CloudRenderer.render(minecraft, ticks, poseStack, modelView, projection, partialTick, cameraX, cameraY, cameraZ);
        }
    }
}
