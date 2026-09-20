package net.krona.climora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.krona.climora.climate.AtmosphereModel;
import net.krona.climora.config.ClimoraConfig;
import net.krona.climora.weather.LocalWeather;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Rain and snow drawn column by column from local weather, replacing the global vanilla weather pass.
 * Based on vanilla {@code LevelRenderer.renderSnowAndRain} and {@code tickRain}, with three changes:
 * <ul>
 *     <li>each column checks its own precipitation, so rain has visible edges;</li>
 *     <li>rain or snow follows the simulated air temperature, not the biome;</li>
 *     <li>streaks slant with the wind.</li>
 * </ul>
 */
public final class PrecipitationRenderer {
    private static final ResourceLocation RAIN_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/rain.png");
    private static final ResourceLocation SNOW_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/snow.png");

    /** Horizontal shift per block of fall for 1 m/s of wind. Rain falls at ~9 m/s, snow at ~1.5 m/s. */
    private static final float RAIN_SLANT_PER_MPS = 1.0F / 9.0F;
    private static final float SNOW_SLANT_PER_MPS = 1.0F / 6.0F;
    private static final float MAX_RAIN_SLANT = 0.6F;
    private static final float MAX_SNOW_SLANT = 1.0F;
    /** Precipitation intensity at which a column is drawn fully opaque. */
    private static final float FULL_INTENSITY = 0.5F;

    private static final float[] RAIN_SIZE_X = new float[1024];
    private static final float[] RAIN_SIZE_Z = new float[1024];
    private static int rainSoundTime;

    static {
        for (int i = 0; i < 32; i++) {
            for (int j = 0; j < 32; j++) {
                float x = j - 16;
                float z = i - 16;
                float length = Mth.sqrt(x * x + z * z);
                RAIN_SIZE_X[i << 5 | j] = -z / length;
                RAIN_SIZE_Z[i << 5 | j] = x / length;
            }
        }
    }

    private PrecipitationRenderer() {
    }

    public static boolean replacesVanilla() {
        return ClientWeather.INSTANCE.isActive() && ClimoraConfig.client().localPrecipitation;
    }

    public static void render(Minecraft minecraft, LightTexture lightTexture, int ticks, float partialTick,
                              double cameraX, double cameraY, double cameraZ) {
        ClientLevel level = minecraft.level;
        ClientWeather weather = ClientWeather.INSTANCE;
        if (level == null) {
            return;
        }
        int radius = Minecraft.useFancyGraphics() ? 10 : 5;
        if (!anyPrecipitationNear(weather, cameraX, cameraZ, radius)) {
            return;
        }

        float slantX = 0.0F;
        float slantZ = 0.0F;
        if (ClimoraConfig.client().windBlownPrecipitation) {
            slantX = weather.windX(cameraX, cameraZ);
            slantZ = weather.windZ(cameraX, cameraZ);
        }
        float rainShiftX = Mth.clamp(-slantX * RAIN_SLANT_PER_MPS, -MAX_RAIN_SLANT, MAX_RAIN_SLANT);
        float rainShiftZ = Mth.clamp(-slantZ * RAIN_SLANT_PER_MPS, -MAX_RAIN_SLANT, MAX_RAIN_SLANT);
        float snowShiftX = Mth.clamp(-slantX * SNOW_SLANT_PER_MPS, -MAX_SNOW_SLANT, MAX_SNOW_SLANT);
        float snowShiftZ = Mth.clamp(-slantZ * SNOW_SLANT_PER_MPS, -MAX_SNOW_SLANT, MAX_SNOW_SLANT);

        lightTexture.turnOnLightLayer();
        int camX = Mth.floor(cameraX);
        int camY = Mth.floor(cameraY);
        int camZ = Mth.floor(cameraZ);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = null;
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getParticleShader);

        int currentType = -1;
        float time = ticks + partialTick;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int z = camZ - radius; z <= camZ + radius; z++) {
            for (int x = camX - radius; x <= camX + radius; x++) {
                float intensity = weather.precipitation(x + 0.5, z + 0.5);
                if (intensity < LocalWeather.PRECIPITATION_THRESHOLD) {
                    continue;
                }
                int sizeIndex = (z - camZ + 16) * 32 + x - camX + 16;
                double halfX = RAIN_SIZE_X[sizeIndex] * 0.5;
                double halfZ = RAIN_SIZE_Z[sizeIndex] * 0.5;

                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int bottom = Math.max(camY - radius, ground);
                int top = Math.max(camY + radius, ground);
                int lightY = Math.max(ground, camY);
                if (bottom == top) {
                    continue;
                }

                float temperature = weather.airTemperature(x + 0.5, lightY, z + 0.5);
                boolean snow = !Float.isNaN(temperature)
                        && AtmosphereModel.precipitationType(temperature) == AtmosphereModel.PrecipitationType.SNOW;
                // Nearly transparent right at the threshold, so the edge of a shower fades in instead of
                // switching on as a block of rain.
                float strength = Mth.clamp((intensity - LocalWeather.PRECIPITATION_THRESHOLD)
                        / (FULL_INTENSITY - LocalWeather.PRECIPITATION_THRESHOLD), 0.04F, 1.0F);
                RandomSource random = RandomSource.create(x * x * 3121 + x * 45238971 ^ z * z * 418711 + z * 13761);
                pos.set(x, lightY, z);
                int light = LevelRenderer.getLightColor(level, pos);

                double distanceX = x + 0.5 - cameraX;
                double distanceZ = z + 0.5 - cameraZ;
                float distance = (float) Math.sqrt(distanceX * distanceX + distanceZ * distanceZ) / radius;
                float fallHeight = top - bottom;

                if (!snow) {
                    if (currentType != 0) {
                        if (currentType >= 0) {
                            BufferUploader.drawWithShader(builder.buildOrThrow());
                        }
                        currentType = 0;
                        RenderSystem.setShaderTexture(0, RAIN_LOCATION);
                        builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                    }
                    int tickOffset = ticks & 131071;
                    int columnOffset = x * x * 3121 + x * 45238971 + z * z * 418711 + z * 13761 & 0xFF;
                    float speed = 3.0F + random.nextFloat();
                    float scroll = -(tickOffset + columnOffset + partialTick) / 32.0F * speed % 32.0F;
                    float alpha = ((1.0F - distance * distance) * 0.5F + 0.5F) * strength;
                    float shiftX = rainShiftX * fallHeight;
                    float shiftZ = rainShiftZ * fallHeight;

                    addQuad(builder, x, z, bottom, top, cameraX, cameraY, cameraZ, halfX, halfZ, shiftX, shiftZ,
                            0.0F, bottom * 0.25F + scroll, top * 0.25F + scroll, alpha, light, false);
                } else {
                    if (currentType != 1) {
                        if (currentType >= 0) {
                            BufferUploader.drawWithShader(builder.buildOrThrow());
                        }
                        currentType = 1;
                        RenderSystem.setShaderTexture(0, SNOW_LOCATION);
                        builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                    }
                    float fall = -((ticks & 511) + partialTick) / 512.0F;
                    float driftU = (float) (random.nextDouble() + time * 0.01 * (float) random.nextGaussian());
                    float driftV = (float) (random.nextDouble() + time * (float) random.nextGaussian() * 0.001);
                    float alpha = ((1.0F - distance * distance) * 0.3F + 0.5F) * strength;
                    float shiftX = snowShiftX * fallHeight;
                    float shiftZ = snowShiftZ * fallHeight;

                    addQuad(builder, x, z, bottom, top, cameraX, cameraY, cameraZ, halfX, halfZ, shiftX, shiftZ,
                            driftU, bottom * 0.25F + fall + driftV, top * 0.25F + fall + driftV, alpha, light, true);
                }
            }
        }

        if (currentType >= 0) {
            BufferUploader.drawWithShader(builder.buildOrThrow());
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        lightTexture.turnOffLightLayer();
    }

    /**
     * One camera-facing precipitation quad. The top edge is shifted against the wind, so streaks lean
     * in the direction the wind blows.
     */
    private static void addQuad(BufferBuilder builder, int x, int z, int bottom, int top,
                                double cameraX, double cameraY, double cameraZ, double halfX, double halfZ,
                                float shiftX, float shiftZ, float u, float vBottom, float vTop, float alpha, int light,
                                boolean snow) {
        float x0 = (float) (x - cameraX - halfX + 0.5);
        float x1 = (float) (x - cameraX + halfX + 0.5);
        float z0 = (float) (z - cameraZ - halfZ + 0.5);
        float z1 = (float) (z - cameraZ + halfZ + 0.5);
        float yTop = (float) (top - cameraY);
        float yBottom = (float) (bottom - cameraY);

        if (!snow) {
            builder.addVertex(x0 + shiftX, yTop, z0 + shiftZ).setUv(u, vBottom).setColor(1.0F, 1.0F, 1.0F, alpha).setLight(light);
            builder.addVertex(x1 + shiftX, yTop, z1 + shiftZ).setUv(u + 1.0F, vBottom).setColor(1.0F, 1.0F, 1.0F, alpha).setLight(light);
            builder.addVertex(x1, yBottom, z1).setUv(u + 1.0F, vTop).setColor(1.0F, 1.0F, 1.0F, alpha).setLight(light);
            builder.addVertex(x0, yBottom, z0).setUv(u, vTop).setColor(1.0F, 1.0F, 1.0F, alpha).setLight(light);
        } else {
            // Snow is brighter than the surrounding light, like vanilla.
            int sky = light >> 16 & 65535;
            int block = light & 65535;
            int skyLight = (sky * 3 + 240) / 4;
            int blockLight = (block * 3 + 240) / 4;
            builder.addVertex(x0 + shiftX, yTop, z0 + shiftZ).setUv(u, vBottom).setColor(1.0F, 1.0F, 1.0F, alpha).setUv2(blockLight, skyLight);
            builder.addVertex(x1 + shiftX, yTop, z1 + shiftZ).setUv(u + 1.0F, vBottom).setColor(1.0F, 1.0F, 1.0F, alpha).setUv2(blockLight, skyLight);
            builder.addVertex(x1, yBottom, z1).setUv(u + 1.0F, vTop).setColor(1.0F, 1.0F, 1.0F, alpha).setUv2(blockLight, skyLight);
            builder.addVertex(x0, yBottom, z0).setUv(u, vTop).setColor(1.0F, 1.0F, 1.0F, alpha).setUv2(blockLight, skyLight);
        }
    }

    /** Cheap early exit: check the corners and center of the rendered area. */
    private static boolean anyPrecipitationNear(ClientWeather weather, double x, double z, int radius) {
        return weather.precipitation(x, z) >= LocalWeather.PRECIPITATION_THRESHOLD
                || weather.precipitation(x - radius, z - radius) >= LocalWeather.PRECIPITATION_THRESHOLD
                || weather.precipitation(x + radius, z - radius) >= LocalWeather.PRECIPITATION_THRESHOLD
                || weather.precipitation(x - radius, z + radius) >= LocalWeather.PRECIPITATION_THRESHOLD
                || weather.precipitation(x + radius, z + radius) >= LocalWeather.PRECIPITATION_THRESHOLD;
    }

    /** Splash particles and rain sounds around the camera, only where it rains (not where it snows). */
    public static void tick(Minecraft minecraft, Camera camera, int ticks) {
        ClientLevel level = minecraft.level;
        ClientWeather weather = ClientWeather.INSTANCE;
        if (level == null) {
            return;
        }
        float rain = weather.rainLevel(1.0F) / (Minecraft.useFancyGraphics() ? 1.0F : 2.0F);
        if (rain <= 0.0F) {
            return;
        }

        RandomSource random = RandomSource.create(ticks * 312987231L);
        BlockPos cameraPos = BlockPos.containing(camera.getPosition());
        BlockPos soundPos = null;
        int attempts = (int) (100.0F * rain * rain) / (minecraft.options.particles().get() == ParticleStatus.DECREASED ? 2 : 1);

        for (int i = 0; i < attempts; i++) {
            int dx = random.nextInt(21) - 10;
            int dz = random.nextInt(21) - 10;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, cameraPos.offset(dx, 0, dz));
            if (top.getY() <= level.getMinBuildHeight() || top.getY() > cameraPos.getY() + 10 || top.getY() < cameraPos.getY() - 10) {
                continue;
            }
            AtmosphereModel.PrecipitationType type = LocalWeather.precipitationAt(weather, top);
            if (type != AtmosphereModel.PrecipitationType.RAIN && type != AtmosphereModel.PrecipitationType.SLEET) {
                continue;
            }
            soundPos = top.below();
            if (minecraft.options.particles().get() == ParticleStatus.MINIMAL) {
                break;
            }

            double offsetX = random.nextDouble();
            double offsetZ = random.nextDouble();
            BlockState state = level.getBlockState(soundPos);
            FluidState fluid = level.getFluidState(soundPos);
            VoxelShape shape = state.getCollisionShape(level, soundPos);
            double height = Math.max(shape.max(Direction.Axis.Y, offsetX, offsetZ), fluid.getHeight(level, soundPos));
            ParticleOptions particle = !fluid.is(FluidTags.LAVA) && !state.is(Blocks.MAGMA_BLOCK) && !CampfireBlock.isLitCampfire(state)
                    ? ParticleTypes.RAIN : ParticleTypes.SMOKE;
            level.addParticle(particle, soundPos.getX() + offsetX, soundPos.getY() + height, soundPos.getZ() + offsetZ, 0.0, 0.0, 0.0);
        }

        if (soundPos != null && random.nextInt(3) < rainSoundTime++) {
            rainSoundTime = 0;
            if (soundPos.getY() > cameraPos.getY() + 1
                    && level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, cameraPos).getY() > Mth.floor((float) cameraPos.getY())) {
                level.playLocalSound(soundPos, SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.WEATHER, 0.1F, 0.5F, false);
            } else {
                level.playLocalSound(soundPos, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, 0.2F, 1.0F, false);
            }
        }
    }
}
