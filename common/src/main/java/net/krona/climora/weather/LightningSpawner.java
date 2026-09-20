package net.krona.climora.weather;

import net.krona.climora.climate.AtmosphereModel;
import net.krona.climora.climate.CellPos;
import net.krona.climora.climate.ClimateCell;
import net.krona.climora.climate.ClimateSimulation;
import net.krona.climora.climate.SimulationConstants;
import net.krona.climora.config.ClimoraConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Lightning strikes inside thunderstorm cells, replacing the global vanilla thunder.
 */
public final class LightningSpawner {
    /** Strike chance per tick in a cell with a full-intensity storm: about one strike every 12 seconds. */
    private static final float STRIKE_CHANCE_AT_FULL_STORM = 1.0F / 240.0F;
    private static final float MIN_STORM = 0.15F;
    private static final int LIGHTNING_ROD_RANGE = 128;

    private final ServerLevel level;

    public LightningSpawner(ServerLevel level) {
        this.level = level;
    }

    public void tick(ClimateSimulation simulation) {
        ClimoraConfig.Server config = ClimoraConfig.server();
        if (!config.localWeather || !config.thunderstorms || config.lightningRate <= 0.0) {
            return;
        }
        RandomSource random = level.getRandom();
        for (long key : simulation.activeCellKeys()) {
            ClimateCell cell = simulation.grid().get(key);
            if (cell == null || cell.storm() < MIN_STORM) {
                continue;
            }
            float chance = cell.storm() * cell.storm() * STRIKE_CHANCE_AT_FULL_STORM * (float) config.lightningRate;
            if (random.nextFloat() >= chance) {
                continue;
            }
            int x = CellPos.minBlock(cell.cellX()) + random.nextInt(SimulationConstants.CELL_SIZE_BLOCKS);
            int z = CellPos.minBlock(cell.cellZ()) + random.nextInt(SimulationConstants.CELL_SIZE_BLOCKS);
            strike(simulation, new BlockPos(x, 0, z));
        }
    }

    private void strike(ClimateSimulation simulation, BlockPos column) {
        if (!level.isPositionEntityTicking(column)) {
            return;
        }
        BlockPos target = findTarget(column);
        if (LocalWeather.precipitationAt(simulation, target) == AtmosphereModel.PrecipitationType.NONE
                || !level.canSeeSky(target)) {
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(target));
            level.addFreshEntity(bolt);
            simulation.stats().recordLightningStrike();
        }
    }

    /** Same rules as vanilla: lightning rods first, then exposed living entities, then the ground. */
    private BlockPos findTarget(BlockPos column) {
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
        Optional<BlockPos> rod = level.getPoiManager().findClosest(
                holder -> holder.is(PoiTypes.LIGHTNING_ROD),
                pos -> pos.getY() == level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 1,
                ground,
                LIGHTNING_ROD_RANGE,
                PoiManager.Occupancy.ANY);
        if (rod.isPresent()) {
            return rod.get().above();
        }

        AABB area = AABB.encapsulatingFullBlocks(ground, ground.atY(level.getMaxBuildHeight())).inflate(3.0);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity.isAlive() && level.canSeeSky(entity.blockPosition()));
        if (!entities.isEmpty()) {
            return entities.get(level.getRandom().nextInt(entities.size())).blockPosition();
        }
        return ground.getY() == level.getMinBuildHeight() - 1 ? ground.above(2) : ground;
    }
}
