package net.krona.climora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.krona.climora.Climora;
import net.krona.climora.client.compat.ParticleRainCompat;
import net.krona.climora.client.compat.SoundPhysicsCompat;
import net.krona.climora.climate.AtmosphereModel;
import net.krona.climora.climate.ClimateSimulation;
import net.krona.climora.config.ClimoraConfig;
import net.krona.climora.registry.ClimoraRegistries;
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
 * Rain, snow, sleet and hail drawn column by column from local weather, replacing the global vanilla
 * weather pass, plus curtains of rain under distant showers.
 * <p>
 * Based on vanilla {@code LevelRenderer.renderSnowAndRain} and {@code tickRain}, with these changes:
 * <ul>
 *     <li>each column checks its own precipitation, so rain has visible edges;</li>
 *     <li>rain or snow follows the simulated air temperature, not the biome;</li>
 *     <li>streaks slant with the wind;</li>
 *     <li>the look follows the intensity: drizzle is sparse and slow, a downpour dense and fast;</li>
 *     <li>sleet is rain and wet snow at once, hail is a layer of fast ice pellets on top;</li>
 *     <li>showers further away than the columns are drawn as grey curtains reaching up to the cloud base.</li>
 * </ul>
 * Columns are gathered once per frame and drawn in one pass per texture.
 */
public final class PrecipitationRenderer {
    private static final ResourceLocation RAIN_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/rain.png");
    private static final ResourceLocation SNOW_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/snow.png");
    private static final ResourceLocation HAIL_LOCATION = ResourceLocation.fromNamespaceAndPath(Climora.MOD_ID, "textures/environment/hail.png");
    /**
     * Soft vertical streaks for distant curtains. The vanilla rain texture is almost all transparent: its
     * thin streaks shrink below a pixel far away and the curtain disappears.
     */
    private static final ResourceLocation CURTAIN_LOCATION = ResourceLocation.fromNamespaceAndPath(Climora.MOD_ID, "textures/environment/rain_curtain.png");

    /** Horizontal shift per block of fall for 1 m/s of wind. Rain falls at ~9 m/s, snow at ~1.5 m/s, hail at ~20 m/s. */
    private static final float RAIN_SLANT_PER_MPS = 1.0F / 9.0F;
    private static final float SNOW_SLANT_PER_MPS = 1.0F / 6.0F;
    private static final float HAIL_SLANT_PER_MPS = 1.0F / 20.0F;
    private static final float MAX_RAIN_SLANT = 0.6F;
    private static final float MAX_SNOW_SLANT = 1.0F;
    private static final float MAX_HAIL_SLANT = 0.3F;
    /** Precipitation intensity at which a column is drawn fully opaque. */
    private static final float FULL_INTENSITY = 0.5F;
    /** Share of columns that carry streaks right at the threshold; drizzle is sparse. */
    private static final float MIN_COVERAGE = 0.35F;
    /** From this intensity on every column carries streaks. */
    private static final float FULL_COVERAGE_INTENSITY = 0.35F;
    /** Above this intensity a second layer of streaks makes a downpour look dense. */
    private static final float DOWNPOUR_START = 0.6F;

    /** Distant showers are drawn on a grid of this step, blocks, aligned to the world so they do not slide. */
    private static final int DISTANT_STEP = 24;
    /** Distant curtains fade in between these distances, where the column rain fades out. */
    private static final float DISTANT_NEAR = 10.0F;
    private static final float DISTANT_FULL = 36.0F;
    /** The client knows the weather of 7x7 cells around the player, about this far. */
    private static final int MAX_DISTANT_RANGE = 448;
    private static final float DISTANT_ALPHA = 0.45F;
    /** How far the foot of a distant curtain drifts from under its cloud per m/s of wind, blocks, and at most. */
    private static final float DISTANT_LEAN_PER_MPS = 0.8F;
    private static final float MAX_DISTANT_LEAN = 10.0F;
    /** Share of the curtain height over which it thins towards the ground. */
    private static final float DISTANT_FOOT = 0.2F;
    /**
     * Horizontal profile of one curtain, from edge to edge: a soft hump three grid steps wide. Neighbouring
     * curtains overlap threefold, so together they make an even veil instead of stripes.
     */
    private static final float[] DISTANT_PROFILE_OFFSET = {-1.0F, -0.5F, 0.0F, 0.5F, 1.0F};
    private static final float[] DISTANT_PROFILE_WEIGHT = {0.0F, 0.6F, 1.0F, 0.6F, 0.0F};
    /** Rain clouds hang this much lower per unit of intensity, blocks; must match {@code CloudRenderer}. */
    private static final float RAIN_CLOUD_LOWERING = 12.0F;

    private static final int MAX_COLUMNS = 32 * 32;
    private static final float[] RAIN_SIZE_X = new float[1024];
    private static final float[] RAIN_SIZE_Z = new float[1024];

    // Columns of the current frame, reused to avoid garbage.
    private static final int[] columnX = new int[MAX_COLUMNS];
    private static final int[] columnZ = new int[MAX_COLUMNS];
    private static final int[] columnBottom = new int[MAX_COLUMNS];
    private static final int[] columnTop = new int[MAX_COLUMNS];
    private static final int[] columnLight = new int[MAX_COLUMNS];
    private static final float[] columnIntensity = new float[MAX_COLUMNS];
    private static final float[] columnHail = new float[MAX_COLUMNS];
    private static final float[] columnDistance = new float[MAX_COLUMNS];
    private static final AtmosphereModel.PrecipitationType[] columnType = new AtmosphereModel.PrecipitationType[MAX_COLUMNS];
    private static int columnCount;

    private static int rainSoundTime;
    /** Hail intensity from which falling hail, its shards and its sound are at full strength. */
    private static final float HAIL_FULL_VISIBILITY = 0.45F;
    /** Splash density at the edge of a shower that has not reached the camera yet, as a rain level. */
    private static final float EDGE_RAIN_LEVEL = 0.5F;
    /** Nearby rain is searched on rings this far apart, out to this distance, in this many directions, blocks. */
    private static final int NEARBY_RAIN_STEP = 8;
    private static final int NEARBY_RAIN_REACH = 48;
    private static final int NEARBY_RAIN_DIRECTIONS = 12;
    /** Nearby rain is played this far from the camera at most, towards the rain, so it is heard from its side. */
    private static final double NEARBY_RAIN_SOURCE = 6.0;
    /** How far to either side of that direction it is spread, blocks. */
    private static final double NEARBY_RAIN_SPREAD = 4.0;
    /** Deeper below the surface than this, blocks, nearby rain is not heard. */
    private static final int NEARBY_RAIN_MAX_DEPTH = 16;
    private static int hailSoundTime;
    /** Curtains drawn in the last frame, for the development harness. */
    private static int lastDistantCurtains;

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

    public static int lastDistantCurtains() {
        return lastDistantCurtains;
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
        boolean near = anyPrecipitationNear(weather, cameraX, cameraZ, radius);
        boolean distant = ClimoraConfig.client().distantRain;
        if (!near && !distant) {
            return;
        }

        float windX = 0.0F;
        float windZ = 0.0F;
        if (ClimoraConfig.client().windBlownPrecipitation) {
            windX = weather.windX(cameraX, cameraZ);
            windZ = weather.windZ(cameraX, cameraZ);
        }

        lightTexture.turnOnLightLayer();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getParticleShader);

        if (near) {
            gatherColumns(level, weather, radius, cameraX, cameraY, cameraZ);
            if (columnCount > 0) {
                // With Particle Rain the falling rain and snow are its particles; hail stays ours.
                if (!ParticleRainCompat.drawsRain()) {
                    renderRain(ticks, partialTick, cameraX, cameraY, cameraZ, windX, windZ);
                }
                if (!ParticleRainCompat.drawsSnow()) {
                    renderSnow(ticks, partialTick, cameraX, cameraY, cameraZ, windX, windZ);
                }
                renderHail(ticks, partialTick, cameraX, cameraY, cameraZ, windX, windZ);
            }
        }
        if (distant) {
            renderDistant(minecraft, level, weather, ticks, partialTick, cameraX, cameraY, cameraZ, windX, windZ);
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        lightTexture.turnOffLightLayer();
    }

    /** Collects the columns around the camera that have precipitation, with everything the passes need. */
    private static void gatherColumns(ClientLevel level, ClientWeather weather, int radius,
                                      double cameraX, double cameraY, double cameraZ) {
        columnCount = 0;
        int camX = Mth.floor(cameraX);
        int camY = Mth.floor(cameraY);
        int camZ = Mth.floor(cameraZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = camZ - radius; z <= camZ + radius; z++) {
            for (int x = camX - radius; x <= camX + radius; x++) {
                float intensity = weather.precipitation(x + 0.5, z + 0.5);
                if (intensity < LocalWeather.PRECIPITATION_THRESHOLD) {
                    continue;
                }
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int bottom = Math.max(camY - radius, ground);
                int top = Math.max(camY + radius, ground);
                if (bottom == top) {
                    continue;
                }
                int lightY = Math.max(ground, camY);
                pos.set(x, lightY, z);

                int i = columnCount++;
                columnX[i] = x;
                columnZ[i] = z;
                columnBottom[i] = bottom;
                columnTop[i] = top;
                columnLight[i] = LevelRenderer.getLightColor(level, pos);
                columnIntensity[i] = intensity;
                columnHail[i] = weather.hail(x + 0.5, z + 0.5);
                columnType[i] = LocalWeather.precipitationType(weather, x + 0.5, lightY, z + 0.5);
                double distanceX = x + 0.5 - cameraX;
                double distanceZ = z + 0.5 - cameraZ;
                columnDistance[i] = (float) Math.sqrt(distanceX * distanceX + distanceZ * distanceZ) / radius;
            }
        }
    }

    /** Rain, freezing rain and the rain part of sleet. */
    private static void renderRain(int ticks, float partialTick, double cameraX, double cameraY, double cameraZ,
                                   float windX, float windZ) {
        float shiftPerBlockX = Mth.clamp(-windX * RAIN_SLANT_PER_MPS, -MAX_RAIN_SLANT, MAX_RAIN_SLANT);
        float shiftPerBlockZ = Mth.clamp(-windZ * RAIN_SLANT_PER_MPS, -MAX_RAIN_SLANT, MAX_RAIN_SLANT);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        int tickOffset = ticks & 131071;

        for (int i = 0; i < columnCount; i++) {
            AtmosphereModel.PrecipitationType type = columnType[i];
            if (!type.isLiquid()) {
                continue;
            }
            int x = columnX[i];
            int z = columnZ[i];
            float intensity = columnIntensity[i];
            RandomSource random = columnRandom(x, z);
            if (random.nextFloat() >= coverage(intensity)) {
                continue;
            }
            boolean sleet = type == AtmosphereModel.PrecipitationType.SLEET;
            float heavy = AtmosphereModel.smoothstep(0.1F, 0.9F, intensity);
            // Big drops fall faster; sleet is half melted snow and falls slower.
            float speed = (2.2F + 2.8F * heavy + random.nextFloat() * 0.8F) * (sleet ? 0.75F : 1.0F);
            int columnOffset = x * x * 3121 + x * 45238971 + z * z * 418711 + z * 13761 & 0xFF;
            float scroll = -(tickOffset + columnOffset + partialTick) / 32.0F * speed % 32.0F;
            float distance = columnDistance[i];
            float alpha = ((1.0F - distance * distance) * 0.5F + 0.5F) * strength(intensity) * (sleet ? 0.65F : 1.0F);
            float fall = columnTop[i] - columnBottom[i];
            // Freezing rain is ordinary rain until it lands; a faint icy tint gives it away.
            float red = type == AtmosphereModel.PrecipitationType.FREEZING_RAIN ? 0.86F : 1.0F;
            float green = type == AtmosphereModel.PrecipitationType.FREEZING_RAIN ? 0.95F : 1.0F;

            addQuad(builder, i, cameraX, cameraY, cameraZ, shiftPerBlockX * fall, shiftPerBlockZ * fall,
                    0.0F, columnBottom[i] * 0.25F + scroll, columnTop[i] * 0.25F + scroll,
                    red, green, 1.0F, alpha, false);

            if (intensity > DOWNPOUR_START && !sleet) {
                // A second, offset layer: in a downpour the streaks are too dense to tell apart.
                float extra = AtmosphereModel.smoothstep(DOWNPOUR_START, 1.0F, intensity);
                float scroll2 = -(tickOffset + columnOffset * 3 + partialTick) / 32.0F * speed * 1.2F % 32.0F;
                addQuad(builder, i, cameraX, cameraY, cameraZ, shiftPerBlockX * fall, shiftPerBlockZ * fall,
                        0.5F, columnBottom[i] * 0.25F + scroll2, columnTop[i] * 0.25F + scroll2,
                        red, green, 1.0F, alpha * extra * 0.8F, false);
            }
        }
        draw(builder, RAIN_LOCATION);
    }

    /** Snow and the wet-snow part of sleet. */
    private static void renderSnow(int ticks, float partialTick, double cameraX, double cameraY, double cameraZ,
                                   float windX, float windZ) {
        float shiftPerBlockX = Mth.clamp(-windX * SNOW_SLANT_PER_MPS, -MAX_SNOW_SLANT, MAX_SNOW_SLANT);
        float shiftPerBlockZ = Mth.clamp(-windZ * SNOW_SLANT_PER_MPS, -MAX_SNOW_SLANT, MAX_SNOW_SLANT);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        float time = ticks + partialTick;

        for (int i = 0; i < columnCount; i++) {
            AtmosphereModel.PrecipitationType type = columnType[i];
            boolean sleet = type == AtmosphereModel.PrecipitationType.SLEET;
            if (type != AtmosphereModel.PrecipitationType.SNOW && !sleet) {
                continue;
            }
            float intensity = columnIntensity[i];
            RandomSource random = columnRandom(columnX[i], columnZ[i]);
            if (random.nextFloat() >= coverage(intensity)) {
                continue;
            }
            // Wet snow is heavy and falls about twice as fast as dry flakes.
            float fallSpeed = sleet ? 1.9F : 1.0F;
            float fallOffset = -((ticks & 511) + partialTick) / 512.0F * fallSpeed;
            float driftU = (float) (random.nextDouble() + time * 0.01 * (float) random.nextGaussian());
            float driftV = (float) (random.nextDouble() + time * (float) random.nextGaussian() * 0.001);
            float distance = columnDistance[i];
            float alpha = ((1.0F - distance * distance) * 0.3F + 0.5F) * strength(intensity) * (sleet ? 0.6F : 1.0F);
            float fall = columnTop[i] - columnBottom[i];

            addQuad(builder, i, cameraX, cameraY, cameraZ, shiftPerBlockX * fall, shiftPerBlockZ * fall,
                    driftU, columnBottom[i] * 0.25F + fallOffset + driftV, columnTop[i] * 0.25F + fallOffset + driftV,
                    1.0F, 1.0F, 1.0F, alpha, true);

            if (intensity > DOWNPOUR_START && !sleet) {
                // Heavy snow: a second layer of flakes drifting differently.
                float extra = AtmosphereModel.smoothstep(DOWNPOUR_START, 1.0F, intensity);
                float drift2 = driftU + 0.37F + time * 0.004F;
                addQuad(builder, i, cameraX, cameraY, cameraZ, shiftPerBlockX * fall, shiftPerBlockZ * fall,
                        drift2, columnBottom[i] * 0.25F + fallOffset * 1.3F + driftV * 0.5F,
                        columnTop[i] * 0.25F + fallOffset * 1.3F + driftV * 0.5F, 1.0F, 1.0F, 1.0F, alpha * extra * 0.8F, true);
            }
        }
        draw(builder, SNOW_LOCATION);
    }

    /** Hail: fast ice pellets on top of the rain of the strongest storms. */
    private static void renderHail(int ticks, float partialTick, double cameraX, double cameraY, double cameraZ,
                                   float windX, float windZ) {
        float shiftPerBlockX = Mth.clamp(-windX * HAIL_SLANT_PER_MPS, -MAX_HAIL_SLANT, MAX_HAIL_SLANT);
        float shiftPerBlockZ = Mth.clamp(-windZ * HAIL_SLANT_PER_MPS, -MAX_HAIL_SLANT, MAX_HAIL_SLANT);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        int tickOffset = ticks & 131071;

        for (int i = 0; i < columnCount; i++) {
            float hail = columnHail[i];
            if (hail < ClimateSimulation.HAIL_THRESHOLD) {
                continue;
            }
            int x = columnX[i];
            int z = columnZ[i];
            RandomSource random = columnRandom(x + 7919, z - 7919);
            if (random.nextFloat() >= 0.3F + 0.7F * AtmosphereModel.smoothstep(ClimateSimulation.HAIL_THRESHOLD, 0.5F, hail)) {
                continue;
            }
            float speed = 7.0F + random.nextFloat() * 2.0F;
            int columnOffset = x * 7919 + z * 104729 & 0xFF;
            float scroll = -(tickOffset + columnOffset + partialTick) / 32.0F * speed % 32.0F;
            float distance = columnDistance[i];
            float alpha = ((1.0F - distance * distance) * 0.4F + 0.6F) * hailVisibility(hail);
            float fall = columnTop[i] - columnBottom[i];
            addQuad(builder, i, cameraX, cameraY, cameraZ, shiftPerBlockX * fall, shiftPerBlockZ * fall,
                    random.nextFloat(), columnBottom[i] * 0.25F + scroll, columnTop[i] * 0.25F + scroll,
                    1.0F, 1.0F, 1.0F, alpha, true);
        }
        draw(builder, HAIL_LOCATION);
    }

    /**
     * Showers beyond the column rain, drawn as camera-facing curtains from the ground to the cloud base.
     * They use the particle shader like the rain, so they fade into fog with distance like everything else.
     */
    private static void renderDistant(Minecraft minecraft, ClientLevel level, ClientWeather weather, int ticks,
                                      float partialTick, double cameraX, double cameraY, double cameraZ,
                                      float windX, float windZ) {
        float cloudHeight = level.effects().getCloudHeight();
        if (Float.isNaN(cloudHeight)) {
            return;
        }
        // As far as the clouds are drawn: a shower under a distant cloud is exactly what should be seen.
        int range = Math.min(MAX_DISTANT_RANGE, CloudRenderer.renderDistance(minecraft));
        if (range <= DISTANT_FULL || weather.maxPrecipitation() < LocalWeather.PRECIPITATION_THRESHOLD) {
            lastDistantCurtains = 0;
            return;
        }
        // The wind drags the foot of a shower a little ahead of its cloud: a slight lean, whatever the height.
        float leanX = Mth.clamp(-windX * DISTANT_LEAN_PER_MPS, -MAX_DISTANT_LEAN, MAX_DISTANT_LEAN);
        float leanZ = Mth.clamp(-windZ * DISTANT_LEAN_PER_MPS, -MAX_DISTANT_LEAN, MAX_DISTANT_LEAN);
        float time = ticks + partialTick;
        int light = LightTexture.pack(0, 15);
        // Curtains show a shower from outside. Standing in the rain there is nothing to see from outside:
        // the column rain and the fog already show how far you can see.
        float outside = 1.0F - AtmosphereModel.smoothstep(LocalWeather.PRECIPITATION_THRESHOLD, 0.35F,
                weather.precipitation(cameraX, cameraZ));
        lastDistantCurtains = 0;
        if (outside <= 0.0F) {
            return;
        }
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int minX = Math.floorDiv(Mth.floor(cameraX) - range, DISTANT_STEP) * DISTANT_STEP;
        int minZ = Math.floorDiv(Mth.floor(cameraZ) - range, DISTANT_STEP) * DISTANT_STEP;
        int maxX = Mth.floor(cameraX) + range;
        int maxZ = Mth.floor(cameraZ) + range;
        float halfWidth = DISTANT_STEP * 1.5F;

        for (int gz = minZ; gz <= maxZ; gz += DISTANT_STEP) {
            for (int gx = minX; gx <= maxX; gx += DISTANT_STEP) {
                double centerX = gx + DISTANT_STEP / 2.0;
                double centerZ = gz + DISTANT_STEP / 2.0;
                double dx = centerX - cameraX;
                double dz = centerZ - cameraZ;
                float distance = (float) Math.sqrt(dx * dx + dz * dz);
                if (distance < DISTANT_NEAR || distance > range) {
                    continue;
                }
                float intensity = weather.precipitation(centerX, centerZ);
                if (intensity < LocalWeather.PRECIPITATION_THRESHOLD) {
                    continue;
                }

                float ground;
                pos.set(gx, 0, gz);
                if (level.hasChunkAt(pos)) {
                    ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) centerX, (int) centerZ);
                } else {
                    float elevation = weather.elevation(centerX, centerZ);
                    ground = Float.isNaN(elevation) ? (float) cameraY - 32.0F : elevation;
                }
                float top = cloudHeight - RAIN_CLOUD_LOWERING * intensity;
                if (top <= ground + 4.0F) {
                    continue;
                }
                AtmosphereModel.PrecipitationType type = LocalWeather.precipitationType(weather, centerX, ground, centerZ);
                boolean snow = type == AtmosphereModel.PrecipitationType.SNOW;

                // Halfway there it must be dry too, or this curtain is seen through rain.
                float between = weather.precipitation(cameraX + dx * 0.5, cameraZ + dz * 0.5);
                // A little variation per curtain, so the veil is not an even sheet.
                float jitter = 0.75F + 0.25F * (((gx * 734287 + gz * 912271) >>> 8 & 255) / 255.0F);
                float alpha = DISTANT_ALPHA * outside * jitter
                        * (1.0F - AtmosphereModel.smoothstep(LocalWeather.PRECIPITATION_THRESHOLD, 0.35F, between))
                        * AtmosphereModel.smoothstep(LocalWeather.PRECIPITATION_THRESHOLD, 0.7F, intensity)
                        * AtmosphereModel.smoothstep(DISTANT_NEAR, DISTANT_FULL, distance)
                        * (1.0F - AtmosphereModel.smoothstep(range * 0.85F, range, distance));
                if (alpha < 0.02F) {
                    continue;
                }
                // Rain shafts are grey like the underside of their cloud, darker than the sky behind. Snow is white.
                float red = snow ? 1.0F : 0.46F;
                float green = snow ? 1.0F : 0.48F;
                float blue = snow ? 1.0F : 0.53F;
                lastDistantCurtains++;

                // Perpendicular to the line of sight, so the curtain always faces the camera.
                float sideX = (float) (-dz / distance) * halfWidth;
                float sideZ = (float) (dx / distance) * halfWidth;
                float height = top - ground;
                float yGround = (float) (ground - cameraY);
                float yFoot = yGround + height * DISTANT_FOOT;
                float yTop = (float) (top - cameraY);

                float u = ((gx * 0.37F + gz * 0.11F) % 1.0F + 1.0F) % 1.0F;
                float speed = snow ? 0.15F : 1.0F;
                float scroll = -(time * 0.004F * speed) % 16.0F;

                // Rain reaches right up into the cloud, which hides the join; towards the ground it thins a
                // little. The top leans with the wind, the ground end stays put.
                float[] heights = {yGround, yFoot, yTop};
                float[] strength = {0.7F, 1.0F, 0.9F};
                for (int band = 0; band < 2; band++) {
                    float yLow = heights[band];
                    float yHigh = heights[band + 1];
                    float leanLow = (yLow - yGround) / height;
                    float leanHigh = (yHigh - yGround) / height;
                    float vLow = (yLow + (float) cameraY) / 64.0F + scroll;
                    float vHigh = (yHigh + (float) cameraY) / 64.0F + scroll;
                    for (int column = 0; column < DISTANT_PROFILE_OFFSET.length - 1; column++) {
                        float t0 = DISTANT_PROFILE_OFFSET[column];
                        float t1 = DISTANT_PROFILE_OFFSET[column + 1];
                        float w0 = DISTANT_PROFILE_WEIGHT[column];
                        float w1 = DISTANT_PROFILE_WEIGHT[column + 1];
                        float x0 = (float) dx + t0 * sideX;
                        float z0 = (float) dz + t0 * sideZ;
                        float x1 = (float) dx + t1 * sideX;
                        float z1 = (float) dz + t1 * sideZ;
                        float u0 = u + t0 * 0.15F;
                        float u1 = u + t1 * 0.15F;
                        builder.addVertex(x0 + leanX * leanHigh, yHigh, z0 + leanZ * leanHigh).setUv(u0, vLow)
                                .setColor(red, green, blue, alpha * w0 * strength[band + 1]).setLight(light);
                        builder.addVertex(x1 + leanX * leanHigh, yHigh, z1 + leanZ * leanHigh).setUv(u1, vLow)
                                .setColor(red, green, blue, alpha * w1 * strength[band + 1]).setLight(light);
                        builder.addVertex(x1 + leanX * leanLow, yLow, z1 + leanZ * leanLow).setUv(u1, vHigh)
                                .setColor(red, green, blue, alpha * w1 * strength[band]).setLight(light);
                        builder.addVertex(x0 + leanX * leanLow, yLow, z0 + leanZ * leanLow).setUv(u0, vHigh)
                                .setColor(red, green, blue, alpha * w0 * strength[band]).setLight(light);
                    }
                }
            }
        }
        // Past the render distance the terrain fog would swallow the curtains whole, while the clouds above
        // them are still drawn. Let them fade into the haze towards the edge of the cloud range instead.
        // In dense weather fog the fog stays as it is: then nothing far away should be seen.
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        boolean clearView = fogEnd >= minecraft.options.getEffectiveRenderDistance() * 16 * 0.9F;
        if (clearView) {
            RenderSystem.setShaderFogStart(Math.max(fogStart, range * 0.45F));
            RenderSystem.setShaderFogEnd(Math.max(fogEnd, range * 1.1F));
        }
        draw(builder, CURTAIN_LOCATION);
        if (clearView) {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
        }
    }

    private static void draw(BufferBuilder builder, ResourceLocation texture) {
        MeshData mesh = builder.build();
        if (mesh != null) {
            RenderSystem.setShaderTexture(0, texture);
            BufferUploader.drawWithShader(mesh);
        }
    }

    /** Same random source for a column every frame, so which columns carry streaks does not flicker. */
    private static RandomSource columnRandom(int x, int z) {
        return RandomSource.create(x * x * 3121 + x * 45238971 ^ z * z * 418711 + z * 13761);
    }

    /** How visible falling hail is, 0..1: nearly transparent just above the threshold, solid from 0.45. */
    private static float hailVisibility(float hail) {
        return AtmosphereModel.smoothstep(ClimateSimulation.HAIL_THRESHOLD, HAIL_FULL_VISIBILITY, hail);
    }

    /** Share of columns with streaks: sparse in drizzle, every column in steady rain. */
    private static float coverage(float intensity) {
        return MIN_COVERAGE + (1.0F - MIN_COVERAGE)
                * AtmosphereModel.smoothstep(LocalWeather.PRECIPITATION_THRESHOLD, FULL_COVERAGE_INTENSITY, intensity);
    }

    /** Nearly transparent right at the threshold, so the edge of a shower fades in instead of switching on. */
    private static float strength(float intensity) {
        return Mth.clamp((intensity - LocalWeather.PRECIPITATION_THRESHOLD)
                / (FULL_INTENSITY - LocalWeather.PRECIPITATION_THRESHOLD), 0.04F, 1.0F);
    }

    /**
     * One camera-facing precipitation quad for column {@code i}. The top edge is shifted against the wind,
     * so streaks lean in the direction the wind blows.
     */
    private static void addQuad(BufferBuilder builder, int i, double cameraX, double cameraY, double cameraZ,
                                float shiftX, float shiftZ, float u, float vBottom, float vTop,
                                float red, float green, float blue, float alpha, boolean bright) {
        int x = columnX[i];
        int z = columnZ[i];
        int sizeIndex = (z - Mth.floor(cameraZ) + 16) * 32 + x - Mth.floor(cameraX) + 16;
        double halfX = RAIN_SIZE_X[sizeIndex] * 0.5;
        double halfZ = RAIN_SIZE_Z[sizeIndex] * 0.5;
        float x0 = (float) (x - cameraX - halfX + 0.5);
        float x1 = (float) (x - cameraX + halfX + 0.5);
        float z0 = (float) (z - cameraZ - halfZ + 0.5);
        float z1 = (float) (z - cameraZ + halfZ + 0.5);
        float yTop = (float) (columnTop[i] - cameraY);
        float yBottom = (float) (columnBottom[i] - cameraY);
        int light = columnLight[i];

        if (!bright) {
            builder.addVertex(x0 + shiftX, yTop, z0 + shiftZ).setUv(u, vBottom).setColor(red, green, blue, alpha).setLight(light);
            builder.addVertex(x1 + shiftX, yTop, z1 + shiftZ).setUv(u + 1.0F, vBottom).setColor(red, green, blue, alpha).setLight(light);
            builder.addVertex(x1, yBottom, z1).setUv(u + 1.0F, vTop).setColor(red, green, blue, alpha).setLight(light);
            builder.addVertex(x0, yBottom, z0).setUv(u, vTop).setColor(red, green, blue, alpha).setLight(light);
        } else {
            // Snow and hail are brighter than the surrounding light, like vanilla snow.
            int sky = light >> 16 & 65535;
            int block = light & 65535;
            int skyLight = (sky * 3 + 240) / 4;
            int blockLight = (block * 3 + 240) / 4;
            builder.addVertex(x0 + shiftX, yTop, z0 + shiftZ).setUv(u, vBottom).setColor(red, green, blue, alpha).setUv2(blockLight, skyLight);
            builder.addVertex(x1 + shiftX, yTop, z1 + shiftZ).setUv(u + 1.0F, vBottom).setColor(red, green, blue, alpha).setUv2(blockLight, skyLight);
            builder.addVertex(x1, yBottom, z1).setUv(u + 1.0F, vTop).setColor(red, green, blue, alpha).setUv2(blockLight, skyLight);
            builder.addVertex(x0, yBottom, z0).setUv(u, vTop).setColor(red, green, blue, alpha).setUv2(blockLight, skyLight);
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

    /**
     * Splash particles and sounds around the camera: rain where drops fall, shattering ice where hail
     * falls. Volume follows the intensity, so drizzle is quiet and a downpour roars.
     */
    public static void tick(Minecraft minecraft, Camera camera, int ticks) {
        ClientLevel level = minecraft.level;
        ClientWeather weather = ClientWeather.INSTANCE;
        if (level == null) {
            return;
        }
        RandomSource random = RandomSource.create(ticks * 312987231L);
        BlockPos cameraPos = BlockPos.containing(camera.getPosition());
        tickRain(minecraft, level, weather, random, cameraPos);
        tickHail(minecraft, level, weather, random, cameraPos);
    }

    private static void tickRain(Minecraft minecraft, ClientLevel level, ClientWeather weather, RandomSource random,
                                 BlockPos cameraPos) {
        // Splashes also where a shower starts a few blocks away while it is still dry over the camera.
        float rain = Math.max(weather.rainLevel(1.0F),
                anyPrecipitationNear(weather, cameraPos.getX() + 0.5, cameraPos.getZ() + 0.5, 10) ? EDGE_RAIN_LEVEL : 0.0F);
        rain /= Minecraft.useFancyGraphics() ? 1.0F : 2.0F;
        BlockPos soundPos = null;
        int attempts = (int) (100.0F * rain * rain) / (minecraft.options.particles().get() == ParticleStatus.DECREASED ? 2 : 1);

        for (int i = 0; i < attempts; i++) {
            int dx = random.nextInt(21) - 10;
            int dz = random.nextInt(21) - 10;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, cameraPos.offset(dx, 0, dz));
            if (top.getY() <= level.getMinBuildHeight() || top.getY() > cameraPos.getY() + 10 || top.getY() < cameraPos.getY() - 10) {
                continue;
            }
            if (!LocalWeather.precipitationAt(weather, top).isLiquid()) {
                continue;
            }
            soundPos = top.below();
            if (minecraft.options.particles().get() == ParticleStatus.MINIMAL || ParticleRainCompat.replacesSplashes()) {
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

        if (random.nextInt(3) < rainSoundTime++) {
            rainSoundTime = 0;
            if (soundPos != null) {
                playRainAround(level, weather, soundPos, cameraPos);
            } else {
                playNearbyRain(level, weather, random, cameraPos);
            }
        }
    }

    /** Rain falling within ten blocks: heard where a drop just landed. */
    private static void playRainAround(ClientLevel level, ClientWeather weather, BlockPos soundPos, BlockPos cameraPos) {
        float loudness = rainLoudness(weather.precipitation(soundPos.getX() + 0.5, soundPos.getZ() + 0.5));
        if (SoundPhysicsCompat.spatialWeather()) {
            // Sound Physics Remastered muffles it behind the roof and the walls itself.
            level.playLocalSound(soundPos, ClimoraRegistries.SPATIAL_RAIN_SOUND.get(), SoundSource.WEATHER, 0.2F * loudness, 1.0F, false);
        } else if (soundPos.getY() > cameraPos.getY() + 1 && isCovered(level, cameraPos)) {
            level.playLocalSound(soundPos, SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.WEATHER, 0.1F * loudness, 0.5F, false);
        } else {
            level.playLocalSound(soundPos, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, 0.2F * loudness, 1.0F, false);
        }
    }

    /**
     * Rain nearby while it is dry over the camera: the nearest rain within {@link #NEARBY_RAIN_REACH} blocks is
     * heard from its side, quieter the farther it is. Walking towards a shower, you hear it before you are in it.
     * Not deep underground, where the surface is far above.
     */
    private static void playNearbyRain(ClientLevel level, ClientWeather weather, RandomSource random, BlockPos cameraPos) {
        if (weather.maxPrecipitation() < LocalWeather.PRECIPITATION_THRESHOLD
                || level.getHeight(Heightmap.Types.MOTION_BLOCKING, cameraPos.getX(), cameraPos.getZ()) - cameraPos.getY() > NEARBY_RAIN_MAX_DEPTH) {
            return;
        }
        double cameraX = cameraPos.getX() + 0.5;
        double cameraZ = cameraPos.getZ() + 0.5;
        for (int distance = NEARBY_RAIN_STEP; distance <= NEARBY_RAIN_REACH; distance += NEARBY_RAIN_STEP) {
            float strongest = 0.0F;
            double towardsX = 0.0;
            double towardsZ = 0.0;
            for (int i = 0; i < NEARBY_RAIN_DIRECTIONS; i++) {
                double angle = Mth.TWO_PI * i / NEARBY_RAIN_DIRECTIONS;
                double x = cameraX + Math.cos(angle) * distance;
                double z = cameraZ + Math.sin(angle) * distance;
                float intensity = weather.precipitation(x, z);
                if (intensity < LocalWeather.PRECIPITATION_THRESHOLD || intensity <= strongest) {
                    continue;
                }
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
                if (!LocalWeather.precipitationType(weather, x, ground, z).isLiquid()) {
                    continue;
                }
                strongest = intensity;
                towardsX = Math.cos(angle);
                towardsZ = Math.sin(angle);
            }
            if (strongest > 0.0F) {
                float fade = 1.0F - AtmosphereModel.smoothstep(NEARBY_RAIN_STEP / 2.0F, NEARBY_RAIN_REACH, distance);
                float volume = 0.2F * rainLoudness(strongest) * fade;
                double source = Math.min(distance, NEARBY_RAIN_SOURCE);
                // Spread sideways, so the rain is a stretch of noise on that side rather than one point.
                double spread = (random.nextDouble() - 0.5) * 2.0 * NEARBY_RAIN_SPREAD;
                double x = cameraX + towardsX * source - towardsZ * spread;
                double z = cameraZ + towardsZ * source + towardsX * spread;
                if (SoundPhysicsCompat.spatialWeather()) {
                    level.playLocalSound(x, cameraPos.getY() + 0.5, z, ClimoraRegistries.SPATIAL_RAIN_SOUND.get(), SoundSource.WEATHER,
                            volume, 1.0F, false);
                } else {
                    // Indoors, rain outside sounds dull through the house.
                    boolean covered = isCovered(level, cameraPos);
                    level.playLocalSound(x, cameraPos.getY() + 0.5, z, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER,
                            covered ? volume * 0.5F : volume, covered ? 0.8F : 1.0F, false);
                }
                return;
            }
        }
    }

    /** Drizzle barely patters; a downpour is several times louder. */
    private static float rainLoudness(float intensity) {
        return (0.35F + 1.4F * AtmosphereModel.smoothstep(LocalWeather.PRECIPITATION_THRESHOLD, 1.0F, intensity))
                * ParticleRainCompat.rainVolume();
    }

    /** Something solid above the camera: a roof, a ceiling, the canopy of a tree. */
    private static boolean isCovered(ClientLevel level, BlockPos cameraPos) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, cameraPos).getY() > Mth.floor((float) cameraPos.getY());
    }

    private static void tickHail(Minecraft minecraft, ClientLevel level, ClientWeather weather, RandomSource random,
                                 BlockPos cameraPos) {
        float hail = weather.hail(cameraPos.getX() + 0.5, cameraPos.getZ() + 0.5);
        // Shards and sound only as far as the falling stones can be seen: faint hail at the edge of a hail
        // shower, which is drawn nearly transparent, must not litter the ground or drum on it.
        float visibility = hailVisibility(hail);
        if (visibility <= 0.0F || minecraft.options.particles().get() == ParticleStatus.MINIMAL) {
            return;
        }
        int attempts = (int) (30.0F * hail * visibility) / (minecraft.options.particles().get() == ParticleStatus.DECREASED ? 2 : 1);
        BlockPos soundPos = null;
        for (int i = 0; i < attempts; i++) {
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
                    cameraPos.offset(random.nextInt(17) - 8, 0, random.nextInt(17) - 8));
            if (Math.abs(top.getY() - cameraPos.getY()) > 8
                    || random.nextFloat() >= hailVisibility(weather.hail(top.getX() + 0.5, top.getZ() + 0.5))) {
                continue;
            }
            // Snowball shards scatter and fall back: stones shattering on the ground.
            level.addParticle(ParticleTypes.ITEM_SNOWBALL,
                    top.getX() + random.nextDouble(), top.getY() + 0.05, top.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
            soundPos = top;
        }
        if (soundPos != null && hailSoundTime++ >= 2) {
            hailSoundTime = 0;
            float volume = (0.25F + 0.5F * hail) * visibility;
            float pitch = 1.3F + random.nextFloat() * 0.6F;
            if (SoundPhysicsCompat.spatialWeather()) {
                // Sound Physics Remastered muffles it behind the roof and the walls itself.
                level.playLocalSound(soundPos, ClimoraRegistries.SPATIAL_HAIL_SOUND.get(), SoundSource.WEATHER, volume, pitch, false);
                return;
            }
            // Under a roof the stones drum on it overhead, duller and quieter, as vanilla does for rain.
            if (soundPos.getY() > cameraPos.getY() + 1 && isCovered(level, cameraPos)) {
                volume *= 0.5F;
                pitch *= 0.6F;
            }
            level.playLocalSound(soundPos, ClimoraRegistries.HAIL_SOUND.get(), SoundSource.WEATHER, volume, pitch, false);
        }
    }
}
