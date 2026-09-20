package net.krona.climora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.krona.climora.config.ClimoraConfig;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Clouds that follow the simulated cloud cover, replacing the vanilla cloud texture.
 * <p>
 * Clouds are boxes on a 12-block grid, like vanilla. Whether a tile is cloudy comes from a fixed noise
 * pattern compared with the local cloud cover, so the sky clears and fills smoothly. The pattern drifts
 * with the weather systems using the offset sent by the server, so every player sees the same clouds.
 * Rain clouds are darker, lower and thicker; storm clouds grow into tall towers.
 * <p>
 * Clouds fade out towards the edge of the drawn area instead of ending at a hard circle, and their tops
 * are uneven, so the sky does not look like a flat plate with a cut-out. Like vanilla they are drawn in
 * two passes, so only the surface of a cloud is visible and the faces inside it are not.
 */
public final class CloudRenderer {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float TILE = 12.0F;
    private static final int REBUILD_INTERVAL_TICKS = 20;
    /** Noise pattern changes shape over this many ticks. */
    private static final double EVOLUTION_TICKS = 6000.0;

    private static final float BASE_THICKNESS = 4.0F;
    private static final float RAIN_THICKNESS = 10.0F;
    private static final float STORM_THICKNESS = 40.0F;
    private static final float RAIN_LOWERING = 12.0F;
    /**
     * A little denser than vanilla. Only the nearest face of a cloud is drawn now, so at vanilla's 0.8 the
     * blue sky showed through the whole cloud and turned it into a blue blob.
     */
    private static final float BASE_ALPHA = 0.92F;
    /** Clouds fade out only at the very edge of the drawn area; distance haze does the rest. */
    private static final float FADE_START = 0.82F;
    /** Weather is sampled every few tiles; cloud cover changes over hundreds of blocks anyway. */
    private static final int WEATHER_SAMPLE_STEP = 4;
    /**
     * Even the darkest storm cloud keeps this share of its brightness. The game already darkens the
     * cloud colour during rain, so darkening them again here turns the sky into a black lid.
     */
    private static final float MIN_SHADE = 0.75F;
    /** Below this alpha a tile is not worth drawing. */
    private static final float MIN_ALPHA = 0.03F;

    // Colour of each face, relative to the sky's cloud colour. Tops catch the light, undersides go blue.
    private static final float[] TOP_TINT = {1.06F, 1.06F, 1.08F};
    private static final float[] BOTTOM_TINT = {0.90F, 0.92F, 0.97F};
    private static final float[] SIDE_X_TINT = {0.94F, 0.95F, 0.99F};
    private static final float[] SIDE_Z_TINT = {0.89F, 0.91F, 0.97F};

    @Nullable
    private static VertexBuffer buffer;
    private static int builtAnchorX = Integer.MIN_VALUE;
    private static int builtAnchorZ = Integer.MIN_VALUE;
    private static int builtTick = Integer.MIN_VALUE;
    private static Vec3 builtColor = Vec3.ZERO;
    @Nullable
    private static CloudStatus builtStatus;
    private static boolean builtEmpty;

    private CloudRenderer() {
    }

    public static boolean replacesVanilla() {
        return ClientWeather.INSTANCE.isActive() && ClimoraConfig.client().dynamicClouds;
    }

    public static void render(Minecraft minecraft, int ticks, PoseStack poseStack, Matrix4f modelView, Matrix4f projection,
                              float partialTick, double cameraX, double cameraY, double cameraZ) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        float cloudHeight = level.effects().getCloudHeight();
        if (Float.isNaN(cloudHeight)) {
            return;
        }

        ClientWeather weather = ClientWeather.INSTANCE;
        double time = level.getGameTime() + partialTick;
        double offsetX = weather.cloudOffsetX(time);
        double offsetZ = weather.cloudOffsetZ(time);
        // Position of the camera in the drifting cloud pattern.
        double patternX = cameraX - offsetX;
        double patternZ = cameraZ - offsetZ;
        int anchorX = Mth.floor(patternX / TILE);
        int anchorZ = Mth.floor(patternZ / TILE);
        Vec3 color = level.getCloudColor(partialTick);
        CloudStatus status = minecraft.options.getCloudsType();

        if (buffer == null || anchorX != builtAnchorX || anchorZ != builtAnchorZ || status != builtStatus
                || ticks - builtTick >= REBUILD_INTERVAL_TICKS || ticks < builtTick
                || builtColor.distanceToSqr(color) > 2.0E-4) {
            rebuild(minecraft, weather, anchorX, anchorZ, offsetX, offsetZ, color, time);
            builtAnchorX = anchorX;
            builtAnchorZ = anchorZ;
            builtTick = ticks;
            builtColor = color;
            builtStatus = status;
        }
        if (buffer == null || builtEmpty) {
            return;
        }

        FogRenderer.levelFogColor();
        poseStack.pushPose();
        poseStack.mulPose(modelView);
        poseStack.translate(anchorX * TILE - patternX, cloudHeight - cameraY, anchorZ * TILE - patternZ);
        buffer.bind();
        RenderType renderType = RenderType.clouds();
        renderType.setupRenderState();
        RenderSystem.setShaderTexture(0, WHITE);
        ShaderInstance shader = RenderSystem.getShader();
        if (shader != null) {
            // Two passes, like vanilla. The first writes depth only, so the second draws just the nearest
            // face of every cloud. In a single pass the tile walls inside a cloud show through it.
            for (int pass = 0; pass < 2; pass++) {
                boolean writeColor = pass == 1;
                RenderSystem.colorMask(writeColor, writeColor, writeColor, writeColor);
                buffer.drawWithShader(poseStack.last().pose(), projection, shader);
            }
        }
        RenderSystem.colorMask(true, true, true, true);
        renderType.clearRenderState();
        VertexBuffer.unbind();
        poseStack.popPose();
    }

    public static void close() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
        builtAnchorX = Integer.MIN_VALUE;
    }

    /** How far clouds are drawn, blocks. 0 in the config means "follow the render distance". */
    private static int renderDistance(Minecraft minecraft) {
        int configured = ClimoraConfig.client().cloudRenderDistance;
        if (configured > 0) {
            return configured;
        }
        return Mth.clamp(minecraft.options.getEffectiveRenderDistance() * 16 + 320, 512, 1024);
    }

    private static void rebuild(Minecraft minecraft, ClientWeather weather, int anchorX, int anchorZ,
                                double offsetX, double offsetZ, Vec3 color, double time) {
        int radius = Math.max(8, Mth.floor(renderDistance(minecraft) / TILE));
        int side = radius * 2 + 1;
        float[] bottoms = new float[side * side];
        float[] tops = new float[side * side];
        float[] shades = new float[side * side];
        float[] alphas = new float[side * side];
        double evolution = time / EVOLUTION_TICKS;

        // Weather is the same over hundreds of blocks, so it is sampled on a coarse grid and reused.
        int coarseSide = side / WEATHER_SAMPLE_STEP + 2;
        float[] coverGrid = new float[coarseSide * coarseSide];
        float[] precipitationGrid = new float[coarseSide * coarseSide];
        float[] stormGrid = new float[coarseSide * coarseSide];
        for (int cz = 0; cz < coarseSide; cz++) {
            for (int cx = 0; cx < coarseSide; cx++) {
                int tileX = anchorX + cx * WEATHER_SAMPLE_STEP - radius;
                int tileZ = anchorZ + cz * WEATHER_SAMPLE_STEP - radius;
                double worldX = (tileX + 0.5) * TILE + offsetX;
                double worldZ = (tileZ + 0.5) * TILE + offsetZ;
                int index = cz * coarseSide + cx;
                coverGrid[index] = weather.cloudCoverClamped(worldX, worldZ);
                precipitationGrid[index] = weather.precipitationClamped(worldX, worldZ);
                stormGrid[index] = weather.stormClamped(worldX, worldZ);
            }
        }

        for (int dz = 0; dz < side; dz++) {
            for (int dx = 0; dx < side; dx++) {
                int index = dz * side + dx;
                tops[index] = Float.NaN;

                // Clouds fade out towards the edge of the drawn area instead of ending abruptly.
                float distance = (float) Math.sqrt((dx - radius) * (dx - radius) + (dz - radius) * (dz - radius)) / radius;
                float alpha = BASE_ALPHA * (1.0F - smoothstep(FADE_START, 1.0F, distance));
                if (alpha < MIN_ALPHA) {
                    continue;
                }

                float cover = coarseValue(coverGrid, coarseSide, dx, dz);
                int tileX = anchorX + dx - radius;
                int tileZ = anchorZ + dz - radius;
                float noise = CloudNoise.sample(tileX, tileZ, evolution);
                if (cover <= 0.02F || noise >= cover) {
                    continue;
                }

                float precipitation = coarseValue(precipitationGrid, coarseSide, dx, dz);
                float storm = coarseValue(stormGrid, coarseSide, dx, dz);
                // Thicker in the middle of a cloud than at its edges, and never perfectly flat on top.
                float core = Mth.clamp((cover - noise) * 4.0F, 0.25F, 1.0F);
                float bumps = Math.round(CloudNoise.shape(tileX, tileZ, evolution) * 4.0F) - 2.0F;
                bottoms[index] = -RAIN_LOWERING * precipitation;
                tops[index] = BASE_THICKNESS + bumps + (RAIN_THICKNESS * precipitation + STORM_THICKNESS * storm) * core;
                shades[index] = Math.max(MIN_SHADE, 1.0F - 0.15F * precipitation - 0.10F * storm);
                alphas[index] = alpha;
            }
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        float r = (float) color.x;
        float g = (float) color.y;
        float b = (float) color.z;

        for (int dz = 0; dz < side; dz++) {
            for (int dx = 0; dx < side; dx++) {
                int index = dz * side + dx;
                float top = tops[index];
                if (Float.isNaN(top)) {
                    continue;
                }
                float bottom = bottoms[index];
                float shade = shades[index];
                float alpha = alphas[index];
                float x0 = (dx - radius) * TILE;
                float z0 = (dz - radius) * TILE;
                float x1 = x0 + TILE;
                float z1 = z0 + TILE;

                quad(builder, x0, top, z0, x0, top, z1, x1, top, z1, x1, top, z0, r, g, b, shade, alpha, TOP_TINT, 0, 1, 0);
                quad(builder, x0, bottom, z0, x1, bottom, z0, x1, bottom, z1, x0, bottom, z1,
                        r, g, b, shade, alpha, BOTTOM_TINT, 0, -1, 0);

                sides(builder, tops, bottoms, side, dx - 1, dz, x0, z0, x0, z1, bottom, top, r, g, b, shade, alpha, SIDE_X_TINT, -1, 0);
                sides(builder, tops, bottoms, side, dx + 1, dz, x1, z1, x1, z0, bottom, top, r, g, b, shade, alpha, SIDE_X_TINT, 1, 0);
                sides(builder, tops, bottoms, side, dx, dz - 1, x1, z0, x0, z0, bottom, top, r, g, b, shade, alpha, SIDE_Z_TINT, 0, -1);
                sides(builder, tops, bottoms, side, dx, dz + 1, x0, z1, x1, z1, bottom, top, r, g, b, shade, alpha, SIDE_Z_TINT, 0, 1);
            }
        }

        MeshData mesh = builder.build();
        builtEmpty = mesh == null;
        if (buffer == null) {
            buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        if (mesh != null) {
            buffer.bind();
            buffer.upload(mesh);
            VertexBuffer.unbind();
        }
    }

    /**
     * Weather for one tile, blended between the four surrounding samples of the coarse grid. Reading the
     * coarse cell directly would make the weather constant over each 48-block square, and the edge of a
     * cloud bank would follow those squares in visible steps.
     */
    private static float coarseValue(float[] grid, int coarseSide, int dx, int dz) {
        float fx = (float) dx / WEATHER_SAMPLE_STEP;
        float fz = (float) dz / WEATHER_SAMPLE_STEP;
        int x0 = (int) fx;
        int z0 = (int) fz;
        int x1 = Math.min(x0 + 1, coarseSide - 1);
        int z1 = Math.min(z0 + 1, coarseSide - 1);
        float tx = fx - x0;
        float tz = fz - z0;
        float near = Mth.lerp(tx, grid[z0 * coarseSide + x0], grid[z0 * coarseSide + x1]);
        float far = Mth.lerp(tx, grid[z1 * coarseSide + x0], grid[z1 * coarseSide + x1]);
        return Mth.lerp(tz, near, far);
    }

    /** Side wall towards a neighbour tile: only the parts not hidden by the neighbour's own box. */
    private static void sides(BufferBuilder builder, float[] tops, float[] bottoms, int side, int nx, int nz,
                              float ax, float az, float bx, float bz, float bottom, float top,
                              float r, float g, float b, float shade, float alpha, float[] tint, int normalX, int normalZ) {
        float neighbourTop = Float.NaN;
        float neighbourBottom = Float.NaN;
        if (nx >= 0 && nz >= 0 && nx < side && nz < side) {
            neighbourTop = tops[nz * side + nx];
            neighbourBottom = bottoms[nz * side + nx];
        }
        if (Float.isNaN(neighbourTop)) {
            wall(builder, ax, az, bx, bz, bottom, top, r, g, b, shade, alpha, tint, normalX, normalZ);
            return;
        }
        if (top > neighbourTop) {
            wall(builder, ax, az, bx, bz, Math.max(bottom, neighbourTop), top, r, g, b, shade, alpha, tint, normalX, normalZ);
        }
        if (bottom < neighbourBottom) {
            wall(builder, ax, az, bx, bz, bottom, Math.min(top, neighbourBottom), r, g, b, shade, alpha, tint, normalX, normalZ);
        }
    }

    private static void wall(BufferBuilder builder, float ax, float az, float bx, float bz, float y0, float y1,
                             float r, float g, float b, float shade, float alpha, float[] tint, int normalX, int normalZ) {
        quad(builder, ax, y0, az, bx, y0, bz, bx, y1, bz, ax, y1, az, r, g, b, shade, alpha, tint, normalX, 0, normalZ);
    }

    private static void quad(BufferBuilder builder,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float shade, float alpha, float[] tint, int nx, int ny, int nz) {
        float cr = Math.min(1.0F, r * shade * tint[0]);
        float cg = Math.min(1.0F, g * shade * tint[1]);
        float cb = Math.min(1.0F, b * shade * tint[2]);
        builder.addVertex(x0, y0, z0).setUv(0.5F, 0.5F).setColor(cr, cg, cb, alpha).setNormal(nx, ny, nz);
        builder.addVertex(x1, y1, z1).setUv(0.5F, 0.5F).setColor(cr, cg, cb, alpha).setNormal(nx, ny, nz);
        builder.addVertex(x2, y2, z2).setUv(0.5F, 0.5F).setColor(cr, cg, cb, alpha).setNormal(nx, ny, nz);
        builder.addVertex(x3, y3, z3).setUv(0.5F, 0.5F).setColor(cr, cg, cb, alpha).setNormal(nx, ny, nz);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
