package net.krona.climora.climate;

import org.jetbrains.annotations.Nullable;

import java.util.function.LongFunction;

/**
 * Smooth climate values between cell centers, so weather has no steps at cell borders.
 */
public final class ClimateInterpolation {
    private ClimateInterpolation() {
    }

    /**
     * Climate at a horizontal position, blended bilinearly from the four nearest cell centers.
     *
     * @param surfaceTemperature air temperature at the interpolated surface height, °C
     * @param surfaceElevation   interpolated surface height, or NaN if no nearby cell knows its elevation
     * @param relativeHumidity   near the ground, 0..~1
     * @param pressure           sea-level pressure, hPa
     * @param windX              near the ground, m/s
     * @param cellCount          number of existing cells that contributed
     */
    public record Sample(float surfaceTemperature, float surfaceElevation, float relativeHumidity, float pressure,
                         float cloudCover, float precipitation, float storm, float windX, float windZ, int cellCount) {
        public Sample {
            // Each cell already keeps its cover at least as high as its rain implies, but the two fields are
            // blended separately and that clamp does not survive blending: between an overcast cell and a
            // clear one the blended cover drops faster than the blended rain does. Re-apply it here, or the
            // edge of a shower drizzles out of a blue sky.
            cloudCover = Math.max(cloudCover, AtmosphereModel.overcastFor(precipitation));
        }

        /** Air temperature at the given height, cooling with altitude above the surface. */
        public float airTemperature(double y) {
            if (Float.isNaN(surfaceElevation)) {
                return surfaceTemperature;
            }
            return surfaceTemperature + ClimateModel.altitudeOffset(surfaceElevation, (float) y);
        }

        public AtmosphereModel.PrecipitationType precipitationType(double y) {
            return AtmosphereModel.precipitationType(airTemperature(y));
        }

        public float windSpeed() {
            return (float) Math.sqrt(windX * windX + windZ * windZ);
        }
    }

    /** Returns null when none of the four surrounding cells exist yet. */
    @Nullable
    public static Sample sample(LongFunction<ClimateCell> cells, double x, double z) {
        double size = SimulationConstants.CELL_SIZE_BLOCKS;
        double fx = (x - size / 2.0) / size;
        double fz = (z - size / 2.0) / size;
        int x0 = (int) Math.floor(fx);
        int z0 = (int) Math.floor(fz);
        double tx = fx - x0;
        double tz = fz - z0;

        double weightSum = 0.0;
        double temperature = 0.0;
        double humidity = 0.0;
        double pressure = 0.0;
        double cloud = 0.0;
        double precipitation = 0.0;
        double storm = 0.0;
        double windX = 0.0;
        double windZ = 0.0;
        double elevationSum = 0.0;
        double elevationWeight = 0.0;
        int count = 0;

        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                ClimateCell cell = cells.apply(CellPos.key(x0 + dx, z0 + dz));
                if (cell == null) {
                    continue;
                }
                double weight = (dx == 0 ? 1.0 - tx : tx) * (dz == 0 ? 1.0 - tz : tz);
                // Keep cells with zero weight in the average when they are the only ones available.
                weight = Math.max(weight, 1.0E-6);
                weightSum += weight;
                temperature += cell.temperature() * weight;
                humidity += cell.relativeHumidity() * weight;
                pressure += cell.pressure() * weight;
                cloud += cell.cloudCover() * weight;
                precipitation += cell.precipitation() * weight;
                storm += cell.storm() * weight;
                windX += cell.windX() * weight;
                windZ += cell.windZ() * weight;
                if (cell.hasElevation()) {
                    elevationSum += cell.elevation() * weight;
                    elevationWeight += weight;
                }
                count++;
            }
        }

        if (count == 0) {
            return null;
        }
        float elevation = elevationWeight > 0.0 ? (float) (elevationSum / elevationWeight) : Float.NaN;
        return new Sample(
                (float) (temperature / weightSum),
                elevation,
                (float) (humidity / weightSum),
                (float) (pressure / weightSum),
                (float) (cloud / weightSum),
                (float) (precipitation / weightSum),
                (float) (storm / weightSum),
                (float) (windX / weightSum),
                (float) (windZ / weightSum),
                count);
    }

    /** Fields that can be interpolated without building a full {@link Sample}. */
    public enum Field {
        TEMPERATURE, PRECIPITATION, STORM
    }

    /**
     * One interpolated field without allocations, for hot paths such as rain checks on every entity.
     * Returns NaN when no cells exist nearby.
     */
    public static float field(LongFunction<ClimateCell> cells, double x, double z, Field field) {
        double size = SimulationConstants.CELL_SIZE_BLOCKS;
        double fx = (x - size / 2.0) / size;
        double fz = (z - size / 2.0) / size;
        int x0 = (int) Math.floor(fx);
        int z0 = (int) Math.floor(fz);
        double tx = fx - x0;
        double tz = fz - z0;

        double sum = 0.0;
        double weightSum = 0.0;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                ClimateCell cell = cells.apply(CellPos.key(x0 + dx, z0 + dz));
                if (cell == null) {
                    continue;
                }
                double weight = Math.max((dx == 0 ? 1.0 - tx : tx) * (dz == 0 ? 1.0 - tz : tz), 1.0E-6);
                float value = switch (field) {
                    case TEMPERATURE -> cell.temperature();
                    case PRECIPITATION -> cell.precipitation();
                    case STORM -> cell.storm();
                };
                sum += value * weight;
                weightSum += weight;
            }
        }
        return weightSum > 0.0 ? (float) (sum / weightSum) : Float.NaN;
    }

    /** Only the moisture and cloud cover at a position, for transporting them with the wind. Null if no cells. */
    @Nullable
    static float[] sampleTransported(LongFunction<ClimateCell> cells, double x, double z) {
        double size = SimulationConstants.CELL_SIZE_BLOCKS;
        double fx = (x - size / 2.0) / size;
        double fz = (z - size / 2.0) / size;
        int x0 = (int) Math.floor(fx);
        int z0 = (int) Math.floor(fz);
        double tx = fx - x0;
        double tz = fz - z0;

        double moistureSum = 0.0;
        double moistureWeight = 0.0;
        double cloudSum = 0.0;
        double cloudWeight = 0.0;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                ClimateCell cell = cells.apply(CellPos.key(x0 + dx, z0 + dz));
                if (cell == null || Float.isNaN(cell.moisture())) {
                    continue;
                }
                double weight = Math.max((dx == 0 ? 1.0 - tx : tx) * (dz == 0 ? 1.0 - tz : tz), 1.0E-6);
                moistureSum += cell.moisture() * weight;
                moistureWeight += weight;
                cloudSum += cell.cloudCover() * weight;
                cloudWeight += weight;
            }
        }
        if (moistureWeight <= 0.0) {
            return null;
        }
        return new float[]{(float) (moistureSum / moistureWeight), (float) (cloudSum / cloudWeight)};
    }
}
