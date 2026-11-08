package net.krona.climora.block;

import com.mojang.serialization.MapCodec;
import net.krona.climora.weather.LocalWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A shallow puddle of rain water on natural ground. It is a rounded blob that joins its neighbours into
 * one sheet of water. Nothing collides with it, anything placed replaces it, and it drops nothing.
 * The weather places and removes it: see {@code LocalWeather.tickGround}.
 * <p>
 * On a dirt path or farmland, whose top is one pixel lower than a full block, the puddle sits one pixel
 * lower too ({@link #LOW}) so it lies on the ground instead of floating.
 */
public class PuddleBlock extends Block {
    public static final MapCodec<PuddleBlock> CODEC = simpleCodec(PuddleBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty LOW = BooleanProperty.create("low");
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);

    public PuddleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
                .setValue(LOW, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, LOW);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        return ground.isFaceSturdy(level, below, Direction.UP) || isLowGround(ground);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        if (direction == Direction.DOWN) {
            return canSurvive(state, level, pos) ? state.setValue(LOW, isLowGround(neighbourState)) : Blocks.AIR.defaultBlockState();
        }
        if (direction.getAxis().isHorizontal()) {
            return state.setValue(property(direction), neighbourState.is(this));
        }
        return state;
    }

    /** A puddle for this spot, joined to the puddles already next to it. */
    public BlockState stateFor(LevelReader level, BlockPos pos) {
        BlockState state = defaultBlockState().setValue(LOW, isLowGround(level.getBlockState(pos.below())));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            state = state.setValue(property(direction), level.getBlockState(pos.relative(direction)).is(this));
        }
        return state;
    }

    /** Drying out and freezing over, see {@code LocalWeather.tickPuddle}. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        LocalWeather.tickPuddle(level, pos);
    }

    /** Walking through a puddle splashes. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide && entity.getDeltaMovement().horizontalDistanceSqr() > 0.003 && level.random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SPLASH, entity.getX(), pos.getY() + 0.06, entity.getZ(),
                    (level.random.nextDouble() - 0.5) * 0.2, 0.1, (level.random.nextDouble() - 0.5) * 0.2);
        }
    }

    /** Ground whose top is a pixel lower than a full block. */
    public static boolean isLowGround(BlockState ground) {
        return ground.is(Blocks.DIRT_PATH) || ground.is(Blocks.FARMLAND);
    }

    private static BooleanProperty property(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }

    /** Ground puddles form on: natural soil and stone, not sand (it drains) or player floors. */
    public static boolean isPuddleGround(BlockState ground) {
        return ground.is(BlockTags.DIRT) || ground.is(BlockTags.BASE_STONE_OVERWORLD) || isLowGround(ground)
                || ground.is(Blocks.GRAVEL) || ground.is(Blocks.CLAY) || ground.is(Blocks.COBBLESTONE)
                || ground.is(Blocks.MOSSY_COBBLESTONE) || ground.is(Blocks.PACKED_MUD);
    }
}
