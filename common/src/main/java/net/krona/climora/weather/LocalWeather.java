package net.krona.climora.weather;

import net.krona.climora.climate.AtmosphereModel;
import net.krona.climora.climate.ClimateManager;
import net.krona.climora.config.ClimoraConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * Entry point for vanilla code patched by mixins: is it raining here, and as what.
 * Works on both sides; when local weather is not active, callers fall back to vanilla behaviour.
 */
public final class LocalWeather {
    /** Below this intensity nothing falls, and nothing is drawn. */
    public static final float PRECIPITATION_THRESHOLD = 0.08F;

    @Nullable
    private static volatile WeatherView clientView;
    @Nullable
    private static volatile SkyWeather clientSky;

    private LocalWeather() {
    }

    /** Called by the client when it starts or stops receiving local weather for its level. */
    public static void setClient(@Nullable WeatherView view, @Nullable SkyWeather sky) {
        clientView = view;
        clientSky = sky;
    }

    /** Rain and thunder levels around the camera, or null when the client uses vanilla weather. */
    @Nullable
    public static SkyWeather clientSky() {
        return clientSky;
    }

    /** The local weather of a level, or null when vanilla weather applies. */
    @Nullable
    public static WeatherView view(Level level) {
        if (level.isClientSide()) {
            return clientView;
        }
        if (level instanceof ServerLevel serverLevel && ClimoraConfig.server().localWeather) {
            return ClimateManager.get(serverLevel);
        }
        return null;
    }

    public static AtmosphereModel.PrecipitationType precipitationAt(WeatherView view, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        if (view.precipitation(x, z) < PRECIPITATION_THRESHOLD) {
            return AtmosphereModel.PrecipitationType.NONE;
        }
        float temperature = view.airTemperature(x, pos.getY(), z);
        return Float.isNaN(temperature) ? AtmosphereModel.PrecipitationType.RAIN : AtmosphereModel.precipitationType(temperature);
    }

    /** Local replacement of {@link Level#isRainingAt}: liquid precipitation reaching this block from the open sky. */
    public static boolean isRainingAt(Level level, WeatherView view, BlockPos pos) {
        AtmosphereModel.PrecipitationType type = precipitationAt(view, pos);
        if (type == AtmosphereModel.PrecipitationType.NONE || type == AtmosphereModel.PrecipitationType.SNOW) {
            return false;
        }
        return level.canSeeSky(pos) && level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() <= pos.getY();
    }

    /** Local replacement of {@link Biome#getPrecipitationAt} for blocks that react to rain and snow. */
    public static Biome.Precipitation vanillaPrecipitationAt(WeatherView view, BlockPos pos) {
        return switch (precipitationAt(view, pos)) {
            case NONE -> Biome.Precipitation.NONE;
            case SNOW -> Biome.Precipitation.SNOW;
            case RAIN, SLEET -> Biome.Precipitation.RAIN;
        };
    }

    /** Above this temperature snow melts, °C. Slightly above freezing, so snow does not flicker. */
    public static final float SNOW_MELT_TEMPERATURE = 1.0F;
    /** Above this temperature ice turns back into water, °C. */
    public static final float ICE_MELT_TEMPERATURE = 2.0F;
    /** Below this temperature still water freezes, °C. */
    public static final float FREEZE_TEMPERATURE = -1.0F;

    /**
     * Melts snow layers and ice when the air is warm, wherever they are — sunlight is not required.
     * Called for the same random columns vanilla uses for snow and ice.
     */
    public static void tickThaw(ServerLevel level, WeatherView view, BlockPos columnPos) {
        if (!ClimoraConfig.server().snowMelting) {
            return;
        }
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, columnPos);
        float temperature = view.airTemperature(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5);
        if (Float.isNaN(temperature)) {
            return;
        }

        if (temperature > SNOW_MELT_TEMPERATURE) {
            BlockState state = level.getBlockState(surface);
            if (state.is(Blocks.SNOW)) {
                int layers = state.getValue(SnowLayerBlock.LAYERS);
                if (layers > 1) {
                    level.setBlockAndUpdate(surface, state.setValue(SnowLayerBlock.LAYERS, layers - 1));
                } else {
                    level.removeBlock(surface, false);
                }
                return;
            }
        }
        if (temperature > ICE_MELT_TEMPERATURE) {
            BlockPos below = surface.below();
            if (level.getBlockState(below).is(Blocks.ICE)) {
                level.setBlockAndUpdate(below, Blocks.WATER.defaultBlockState());
            }
        }
    }

    /** Local replacement of {@link Biome#shouldFreeze}: our temperature instead of the biome's. */
    public static boolean shouldFreeze(ServerLevel level, WeatherView view, BlockPos pos) {
        if (!ClimoraConfig.server().waterFreezing) {
            return false;
        }
        float temperature = view.airTemperature(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (Float.isNaN(temperature) || temperature > FREEZE_TEMPERATURE) {
            return false;
        }
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                || level.getBrightness(LightLayer.BLOCK, pos) >= 10) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        FluidState fluid = level.getFluidState(pos);
        if (fluid.getType() != Fluids.WATER || !(state.getBlock() instanceof LiquidBlock)) {
            return false;
        }
        // Like vanilla: only the edge of a water body freezes over.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!level.getFluidState(pos.relative(direction)).is(Fluids.WATER)) {
                return true;
            }
        }
        return false;
    }

    /** Local replacement of {@link Biome#shouldSnow}: our temperature instead of the biome's, same placement rules. */
    public static boolean shouldSnow(Level level, WeatherView view, BlockPos pos) {
        if (!ClimoraConfig.server().snowAccumulation
                || precipitationAt(view, pos) != AtmosphereModel.PrecipitationType.SNOW) {
            return false;
        }
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                || level.getBrightness(LightLayer.BLOCK, pos) >= 10) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return (state.isAir() || state.is(Blocks.SNOW)) && Blocks.SNOW.defaultBlockState().canSurvive(level, pos);
    }
}
