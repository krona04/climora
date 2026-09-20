package net.krona.climora.block;

import com.mojang.serialization.MapCodec;
import net.krona.climora.registry.ClimoraRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A copper weather vane. The arrow turns to face the wind, and a comparator reads the wind strength.
 */
public final class WeatherVaneBlock extends BaseEntityBlock {
    public static final MapCodec<WeatherVaneBlock> CODEC = simpleCodec(WeatherVaneBlock::new);

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(4.0, 0.0, 4.0, 12.0, 2.0, 12.0),
            Block.box(7.0, 2.0, 7.0, 9.0, 15.0, 9.0));

    public WeatherVaneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WeatherVaneBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ClimoraRegistries.WEATHER_VANE_ENTITY.get(),
                level.isClientSide() ? WeatherVaneBlockEntity::clientTick : WeatherVaneBlockEntity::serverTick);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof WeatherVaneBlockEntity vane ? vane.signal() : 0;
    }
}
