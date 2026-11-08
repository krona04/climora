package net.krona.climora.weather;

import net.krona.climora.block.GlazeBlock;
import net.krona.climora.block.PuddleBlock;
import net.krona.climora.climate.AtmosphereModel;
import net.krona.climora.climate.ClimateManager;
import net.krona.climora.config.ClimoraConfig;
import net.krona.climora.registry.ClimoraRegistries;
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
    /** From this storm intensity on it counts as a thunderstorm: the same point where lightning starts. */
    public static final float THUNDERSTORM = LightningSpawner.MIN_STORM;
    /** Puddles form in hollows once the ground is this wet, and dry out below {@link #PUDDLE_DRY}. */
    public static final float PUDDLE_FORM = 0.55F;
    public static final float PUDDLE_DRY = 0.25F;
    /** When the ground is this soaked, puddles spread over flat ground next to other puddles. */
    public static final float PUDDLE_FLOOD = 0.85F;

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
        return precipitationType(view, x, pos.getY(), z);
    }

    /** What precipitation at this spot would fall as, whether or not it is falling right now. */
    public static AtmosphereModel.PrecipitationType precipitationType(WeatherView view, double x, double y, double z) {
        float temperature = view.airTemperature(x, y, z);
        if (Float.isNaN(temperature)) {
            return AtmosphereModel.PrecipitationType.RAIN;
        }
        // The air aloft is unknown (NaN) where freezing rain is switched off, which leaves plain rain and snow.
        return AtmosphereModel.precipitationType(temperature, view.columnTemperature(x, y, z));
    }

    /**
     * Local answer to the global {@link Level#isRaining()} that some vanilla code asks: is anything falling
     * around this position? Unlike {@link #isRainingAt}, shelter does not matter: a bee in its hive still
     * knows it rains outside.
     */
    public static boolean isRainingAround(WeatherView view, BlockPos pos) {
        return view.precipitation(pos.getX() + 0.5, pos.getZ() + 0.5) >= PRECIPITATION_THRESHOLD;
    }

    /** Local answer to the global {@link Level#isThundering()}: is there a thunderstorm around this position? */
    public static boolean isThunderingAround(WeatherView view, BlockPos pos) {
        return isRainingAround(view, pos) && view.storm(pos.getX() + 0.5, pos.getZ() + 0.5) >= THUNDERSTORM;
    }

    /** Whether a local thunderstorm is over a position; false where vanilla weather applies. */
    public static boolean isThunderingOver(Level level, BlockPos pos) {
        WeatherView view = view(level);
        return view != null && isThunderingAround(view, pos);
    }

    /** Local replacement of {@link Level#isRainingAt}: liquid precipitation reaching this block from the open sky. */
    public static boolean isRainingAt(Level level, WeatherView view, BlockPos pos) {
        if (!precipitationAt(view, pos).isLiquid()) {
            return false;
        }
        return level.canSeeSky(pos) && level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() <= pos.getY();
    }

    /** Local replacement of {@link Biome#getPrecipitationAt} for blocks that react to rain and snow. */
    public static Biome.Precipitation vanillaPrecipitationAt(WeatherView view, BlockPos pos) {
        return switch (precipitationAt(view, pos)) {
            case NONE -> Biome.Precipitation.NONE;
            case SNOW -> Biome.Precipitation.SNOW;
            case RAIN, SLEET, FREEZING_RAIN -> Biome.Precipitation.RAIN;
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

    /** Columns of the chunk tried per call: one random column per call would take minutes to reach a hollow. */
    private static final int GROUND_ATTEMPTS = 4;

    /**
     * Freezing rain glazes the ground and puddles form in hollows of wet ground. Called for the same random
     * columns vanilla uses for snow and ice, plus a few more of the same chunk. Drying, freezing and
     * melting are done by the blocks themselves on random ticks.
     */
    public static void tickGround(ServerLevel level, WeatherView view, BlockPos columnPos) {
        int chunkX = columnPos.getX() & ~15;
        int chunkZ = columnPos.getZ() & ~15;
        for (int i = 0; i < GROUND_ATTEMPTS; i++) {
            BlockPos column = i == 0 ? columnPos
                    : new BlockPos(chunkX + level.random.nextInt(16), columnPos.getY(), chunkZ + level.random.nextInt(16));
            tickGroundColumn(level, view, column);
        }
    }

    private static void tickGroundColumn(ServerLevel level, WeatherView view, BlockPos columnPos) {
        ClimoraConfig.Server config = ClimoraConfig.server();
        // Puddles do not block motion, so the heightmap stands on the ground under them.
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, columnPos);
        double x = surface.getX() + 0.5;
        double z = surface.getZ() + 0.5;
        if (!level.getBlockState(surface).isAir()) {
            return;
        }
        PuddleBlock puddle = ClimoraRegistries.PUDDLE.get();

        AtmosphereModel.PrecipitationType type = precipitationAt(view, surface);
        if (type == AtmosphereModel.PrecipitationType.FREEZING_RAIN) {
            if (config.freezingRain && level.random.nextBoolean() && GlazeBlock.canCoat(level, surface)
                    && level.canSeeSky(surface)) {
                level.setBlockAndUpdate(surface, ClimoraRegistries.GLAZE.get().defaultBlockState());
            }
            return;
        }
        if (!config.puddles || !type.isLiquid()) {
            return;
        }
        float wetness = view.wetness(x, z);
        if (wetness < PUDDLE_FORM || !PuddleBlock.isPuddleGround(level.getBlockState(surface.below()))
                || !puddle.defaultBlockState().canSurvive(level, surface) || !isHollow(level, surface, wetness)) {
            return;
        }
        level.setBlockAndUpdate(surface, puddle.stateFor(level, surface));
    }

    /** Random tick of a puddle: it dries out with the ground and freezes over in frost. */
    public static void tickPuddle(ServerLevel level, BlockPos pos) {
        WeatherView view = view(level);
        ClimoraConfig.Server config = ClimoraConfig.server();
        if (view == null || !config.puddles) {
            // Without local weather there is nothing to keep a puddle: it soaks away.
            level.removeBlock(pos, false);
            return;
        }
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        float temperature = view.airTemperature(x, pos.getY(), z);
        if (config.waterFreezing && !Float.isNaN(temperature) && temperature <= FREEZE_TEMPERATURE) {
            // A frozen puddle is a patch of ice on the path.
            level.setBlockAndUpdate(pos, ClimoraRegistries.GLAZE.get().defaultBlockState());
        } else if (view.wetness(x, z) < PUDDLE_DRY) {
            level.removeBlock(pos, false);
        }
    }

    /** Random tick of glaze ice: a pixel-thin crust melts as soon as the air is above freezing. */
    public static void tickGlaze(ServerLevel level, BlockPos pos) {
        WeatherView view = view(level);
        float temperature = view == null ? Float.NaN : view.airTemperature(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (view == null || !Float.isNaN(temperature) && temperature > SNOW_MELT_TEMPERATURE) {
            level.removeBlock(pos, false);
        }
    }

    /**
     * Water gathers where it cannot run off: a spot walled in on at least two sides. When the ground is
     * soaked, water also fills a spot mostly surrounded by puddles, so a pit fills up to its middle; an
     * open edge of a sheet of puddles does not grow, so it never spreads over a flat meadow.
     */
    private static boolean isHollow(ServerLevel level, BlockPos pos, float wetness) {
        int walls = 0;
        int puddles = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            BlockState state = level.getBlockState(side);
            if (state.isCollisionShapeFullBlock(level, side)) {
                walls++;
            } else if (state.is(ClimoraRegistries.PUDDLE.get())) {
                puddles++;
            }
        }
        return walls >= 2 || wetness >= PUDDLE_FLOOD && walls + puddles >= 3;
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
