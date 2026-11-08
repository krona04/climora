package net.krona.climora.block;

import com.mojang.serialization.MapCodec;
import net.krona.climora.weather.LocalWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A thin crust of clear ice left by freezing rain, or by a puddle that froze over. As slippery as ice
 * ({@code EntityMixin} makes it the block an entity walks on), breaks at a touch, drops nothing and
 * melts once the air is above freezing.
 */
public class GlazeBlock extends Block {
    public static final MapCodec<GlazeBlock> CODEC = simpleCodec(GlazeBlock::new);
    /** Same as ice. Applies because {@code EntityMixin} makes the crust the block an entity walks on. */
    public static final float FRICTION = 0.98F;
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);

    public GlazeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canCoat(level, pos);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbourState, level, pos, neighbourPos);
    }

    /** Melting, see {@code LocalWeather.tickGlaze}. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        LocalWeather.tickGlaze(level, pos);
    }

    /** Ice can only coat the flat top of a solid block. */
    public static boolean canCoat(LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }
}
