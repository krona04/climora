package net.krona.climora.climate;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Builds a new cell from the biomes and terrain on its surface. Never loads or generates chunks.
 */
public final class BiomeClimateSampler {
    /** Sample points relative to the cell center, in fractions of the cell size. */
    private static final float[][] SAMPLE_OFFSETS = {
            {0.0F, 0.0F}, {-0.25F, -0.25F}, {0.25F, -0.25F}, {-0.25F, 0.25F}, {0.25F, 0.25F}
    };

    private BiomeClimateSampler() {
    }

    public static ClimateCell createCell(ServerLevel level, int cellX, int cellZ, long gameTime) {
        float temperatureSum = 0.0F;
        float amplitudeSum = 0.0F;
        float humiditySum = 0.0F;
        int waterSamples = 0;
        int heightSum = 0;
        // Asking the generator for a height costs about a millisecond, so it is done at most once per cell.
        int generatedHeight = Integer.MIN_VALUE;

        for (float[] offset : SAMPLE_OFFSETS) {
            int x = CellPos.centerBlock(cellX) + (int) (offset[0] * SimulationConstants.CELL_SIZE_BLOCKS);
            int z = CellPos.centerBlock(cellZ) + (int) (offset[1] * SimulationConstants.CELL_SIZE_BLOCKS);
            int y = loadedSurfaceHeight(level, x, z);
            if (y == Integer.MIN_VALUE) {
                if (generatedHeight == Integer.MIN_VALUE) {
                    generatedHeight = generatedSurfaceHeight(level, CellPos.centerBlock(cellX), CellPos.centerBlock(cellZ));
                }
                y = generatedHeight;
            }

            Holder<Biome> biome = level.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
            ClimateProfile profile = ClimateProfiles.of(biome);
            // Each sample brings its own height, so a cell on a slope is colder than one in the valley.
            temperatureSum += profile.meanTemperatureAt(y);
            amplitudeSum += profile.diurnalAmplitude();
            humiditySum += profile.humidity();
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER)) {
                waterSamples++;
            }
            heightSum += y;
        }

        int samples = SAMPLE_OFFSETS.length;
        float baseTemperature = temperatureSum / samples;
        float baseAmplitude = amplitudeSum / samples;
        float baseHumidity = humiditySum / samples;
        float waterFraction = waterSamples / (float) samples;
        int elevation = Math.round(heightSum / (float) samples);
        float temperature = ClimateModel.targetTemperature(baseTemperature, baseAmplitude, level.getDayTime());
        return new ClimateCell(cellX, cellZ, baseTemperature, baseAmplitude, baseHumidity, waterFraction,
                elevation, temperature, gameTime);
    }

    /** Real surface height if the chunk is loaded, otherwise {@code Integer.MIN_VALUE}. */
    private static int loadedSurfaceHeight(ServerLevel level, int x, int z) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        return chunk != null ? chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) : Integer.MIN_VALUE;
    }

    /** Surface height the world generator would produce, without generating the chunk. */
    private static int generatedSurfaceHeight(ServerLevel level, int x, int z) {
        return level.getChunkSource().getGenerator().getBaseHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
    }
}
