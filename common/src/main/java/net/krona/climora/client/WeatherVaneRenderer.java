package net.krona.climora.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.krona.climora.Climora;
import net.krona.climora.block.WeatherVaneBlockEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The turning arrow of the weather vane. The post is a regular block model.
 */
public final class WeatherVaneRenderer implements BlockEntityRenderer<WeatherVaneBlockEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Climora.MOD_ID, "weather_vane"), "main");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Climora.MOD_ID, "textures/entity/weather_vane.png");
    /** Height of the arrow axis inside the block, blocks. */
    private static final double ARROW_HEIGHT = 13.5 / 16.0;

    private final ModelPart arrow;

    public WeatherVaneRenderer(BlockEntityRendererProvider.Context context) {
        this.arrow = context.bakeLayer(LAYER).getChild("arrow");
    }

    /** Arrow along +X: head at +X points to where the wind comes from, the tail fin catches the wind at -X. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("arrow", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-6.0F, -0.5F, -0.5F, 11.0F, 1.0F, 1.0F)
                        .texOffs(0, 2).addBox(5.0F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F)
                        .texOffs(4, 2).addBox(6.0F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F)
                        .texOffs(0, 8).addBox(-8.0F, -2.5F, -0.25F, 3.0F, 5.0F, 0.5F)
                        .texOffs(8, 2).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void render(WeatherVaneBlockEntity vane, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light, int overlay) {
        poseStack.pushPose();
        poseStack.translate(0.5, ARROW_HEIGHT, 0.5);
        poseStack.mulPose(Axis.YP.rotation(-vane.angle(partialTick)));
        arrow.render(poseStack, buffers.getBuffer(RenderType.entityCutout(TEXTURE)), light, overlay);
        poseStack.popPose();
    }
}
