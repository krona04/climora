package net.krona.climora.climate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;

/**
 * The climate of the biome under a point, within its cell.
 * <p>
 * A cell is 128 blocks wide and takes the average climate of the biomes in it. Where a snowfield meets a
 * meadow inside one cell, that average is mild, and the snowfield thawed at its edge every day. Air does mix
 * over that distance, but not completely: so the temperature at a point is moved most of the way from the
 * cell average towards what its own biome would have at that hour.
 */
public final class Microclimate {
    /** How far the point moves from the cell average towards its own biome. */
    public static final float SHARE = 0.8F;

    private Microclimate() {
    }

    /**
     * Correction to add to the cell-blended temperature at a position, °C. 0 where the chunk is not loaded
     * or the cell base values are unknown.
     *
     * @param cellBase      daily mean of the cells blended at this position, °C, at {@code cellElevation}
     * @param cellAmplitude half of the day/night swing of the blended cells, °C
     * @param cloudCover    clouds damp the day/night swing of the biome as they do the cell's
     */
    public static float offset(LevelReader level, double x, double y, double z, float cellBase, float cellAmplitude,
                               float cellElevation, float cloudCover, long dayTime) {
        if (Float.isNaN(cellBase) || Float.isNaN(cellAmplitude) || Float.isNaN(cellElevation)) {
            return 0.0F;
        }
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!level.hasChunkAt(pos)) {
            return 0.0F;
        }
        ClimateProfile profile = ClimateProfiles.of(level.getBiome(pos));
        float diurnal = ClimateModel.diurnalOffset(1.0F, dayTime) * (1.0F - AtmosphereModel.CLOUD_DIURNAL_DAMPING * cloudCover);
        return offset(profile, cellBase, cellAmplitude, Math.round(cellElevation), diurnal);
    }

    /**
     * Same, for a known biome profile.
     *
     * @param diurnal day/night phase, -1 at the coldest hour to 1 at the warmest, already damped by clouds
     */
    public static float offset(ClimateProfile profile, float cellBase, float cellAmplitude, int cellElevation, float diurnal) {
        float local = profile.meanTemperatureAt(cellElevation) + profile.diurnalAmplitude() * diurnal;
        float cell = cellBase + cellAmplitude * diurnal;
        return SHARE * (local - cell);
    }
}
