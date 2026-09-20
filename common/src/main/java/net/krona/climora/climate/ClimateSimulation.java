package net.krona.climora.climate;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.krona.climora.config.ClimoraConfig;
import net.krona.climora.weather.LightningSpawner;
import net.krona.climora.weather.WeatherView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.krona.climora.climate.SimulationConstants.ACTIVE_REFRESH_INTERVAL_TICKS;
import static net.krona.climora.climate.SimulationConstants.CELL_SIZE_BLOCKS;
import static net.krona.climora.climate.SimulationConstants.FULL_PASS_TICKS;
import static net.krona.climora.climate.SimulationConstants.NEIGHBOUR_COUPLING;
import static net.krona.climora.climate.SimulationConstants.THERMAL_TIME_CONSTANT_TICKS;

/**
 * Runs the climate grid of one dimension on the server thread.
 * <p>
 * Only cells near players are simulated. They are updated round-robin within a time budget,
 * and cells left behind catch up when they become active again.
 */
public final class ClimateSimulation implements WeatherView {
    /** Neighbour pressure older than this is ignored in favour of the synoptic field, ticks. */
    private static final long STALE_PRESSURE_TICKS = 200;

    private final ServerLevel level;
    private final ClimateGrid grid;
    private final SynopticField synoptic;
    private final SimulationStats stats = new SimulationStats();
    private final LightningSpawner lightning;
    private final List<WeatherOverride> overrides = new ArrayList<>();

    /** Missing cells near players, nearest first. */
    private final LongLinkedOpenHashSet pendingCells = new LongLinkedOpenHashSet();
    /** Cells to build again from the terrain, for example after the biome there changed. */
    private final LongOpenHashSet resampleRequests = new LongOpenHashSet();
    private long[] activeCells = new long[0];
    private int cursor;
    private boolean refreshNeeded = true;
    /** Extra radius for load tests, set by {@code /climora stress}. */
    private int stressRadius;

    public ClimateSimulation(ServerLevel level, ClimateGrid grid) {
        this.level = level;
        this.grid = grid;
        this.synoptic = new SynopticField(level.getSeed());
        this.lightning = new LightningSpawner(level);
    }

    public ServerLevel level() {
        return level;
    }

    public ClimateGrid grid() {
        return grid;
    }

    public SynopticField synoptic() {
        return synoptic;
    }

    public SimulationStats stats() {
        return stats;
    }

    public int activeCellCount() {
        return activeCells.length;
    }

    /** Keys of the simulated cells. Do not modify the array. */
    public long[] activeCellKeys() {
        return activeCells;
    }

    @Override
    public float precipitation(double x, double z) {
        float value = ClimateInterpolation.field(grid::get, x, z, ClimateInterpolation.Field.PRECIPITATION);
        return Float.isNaN(value) ? 0.0F : value;
    }

    @Override
    public float storm(double x, double z) {
        float value = ClimateInterpolation.field(grid::get, x, z, ClimateInterpolation.Field.STORM);
        return Float.isNaN(value) ? 0.0F : value;
    }

    @Override
    public float windX(double x, double z) {
        ClimateInterpolation.Sample sample = sample(x, z);
        return sample != null ? sample.windX() : 0.0F;
    }

    @Override
    public float windZ(double x, double z) {
        ClimateInterpolation.Sample sample = sample(x, z);
        return sample != null ? sample.windZ() : 0.0F;
    }

    @Override
    public float airTemperature(double x, double y, double z) {
        ClimateInterpolation.Sample sample = sample(x, z);
        return sample != null ? sample.airTemperature(y) : Float.NaN;
    }

    public int pendingCellCount() {
        return pendingCells.size();
    }

    public int stressRadius() {
        return stressRadius;
    }

    public void setStressRadius(int stressRadius) {
        this.stressRadius = Math.max(0, stressRadius);
        this.refreshNeeded = true;
    }

    /**
     * Asks for cells around a position to be built from the terrain again, keeping their weather.
     * Cells are re-sampled when they are active, at the usual rate limit.
     *
     * @return how many cells were queued
     */
    public int requestResample(int centerCellX, int centerCellZ, int radiusCells) {
        int queued = 0;
        for (int dx = -radiusCells; dx <= radiusCells; dx++) {
            for (int dz = -radiusCells; dz <= radiusCells; dz++) {
                if (resampleRequests.add(CellPos.key(centerCellX + dx, centerCellZ + dz))) {
                    queued++;
                }
            }
        }
        refreshNeeded = true;
        return queued;
    }

    public int pendingResampleCount() {
        return resampleRequests.size();
    }

    public void addOverride(WeatherOverride override) {
        overrides.add(override);
    }

    /** Removes overrides covering the given cell. Returns how many were removed. */
    public int clearOverrides(int cellX, int cellZ) {
        int before = overrides.size();
        overrides.removeIf(override -> override.covers(cellX, cellZ));
        return before - overrides.size();
    }

    @Nullable
    public WeatherOverride overrideAt(int cellX, int cellZ) {
        long gameTime = level.getGameTime();
        // The newest override wins.
        for (int i = overrides.size() - 1; i >= 0; i--) {
            WeatherOverride override = overrides.get(i);
            if (override.isActive(gameTime) && override.covers(cellX, cellZ)) {
                return override;
            }
        }
        return null;
    }

    public void tick() {
        long start = System.nanoTime();
        long deadline = start + (long) (ClimoraConfig.server().tickBudgetMillis * 1_000_000L);
        long gameTime = level.getGameTime();

        if (refreshNeeded || gameTime % ACTIVE_REFRESH_INTERVAL_TICKS == 0) {
            refreshActiveCells();
            refreshNeeded = false;
            overrides.removeIf(override -> !override.isActive(gameTime));
        }

        int created = samplePendingCells(gameTime);
        int updated = updateActiveCells(gameTime, deadline);
        if (created > 0 || updated > 0) {
            grid.setDirty();
        }
        lightning.tick(this);

        long end = System.nanoTime();
        stats.recordTick(end - start, created, updated, end > deadline);
    }

    /** Temperature the cell tends to right now, with clouds, precipitation and neighbours. */
    public float targetTemperature(ClimateCell cell) {
        float meanTemperature = meanTemperature(cell, cell.precipitation());
        float diurnal = ClimateModel.diurnalOffset(cell.baseAmplitude(), level.getDayTime())
                * (1.0F - AtmosphereModel.CLOUD_DIURNAL_DAMPING * cell.cloudCover());
        float target = meanTemperature + diurnal;
        float neighbourMean = neighbourMeanTemperature(cell);
        return Float.isNaN(neighbourMean) ? target : Mth.lerp(NEIGHBOUR_COUPLING, target, neighbourMean);
    }

    /** Climate at a horizontal block position, interpolated between cells. Null if no cell exists nearby. */
    @Nullable
    public ClimateInterpolation.Sample sample(double x, double z) {
        return ClimateInterpolation.sample(grid::get, x, z);
    }

    private void refreshActiveCells() {
        int radius = ClimoraConfig.server().activeRadiusCells + stressRadius;
        LongLinkedOpenHashSet active = new LongLinkedOpenHashSet();
        for (ServerPlayer player : level.players()) {
            int centerX = CellPos.blockToCell(player.getBlockX());
            int centerZ = CellPos.blockToCell(player.getBlockZ());
            // Ring by ring, so the cells closest to the player are sampled first.
            for (int ring = 0; ring <= radius; ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                            active.add(CellPos.key(centerX + dx, centerZ + dz));
                        }
                    }
                }
            }
        }

        pendingCells.clear();
        for (long key : active) {
            if (needsSampling(grid.get(key)) || resampleRequests.contains(key)) {
                pendingCells.add(key);
            }
        }
        // Requests for cells nobody is near cannot be served; drop them.
        resampleRequests.removeIf((long key) -> !active.contains(key));

        activeCells = active.toLongArray();
        if (cursor >= activeCells.length) {
            cursor = 0;
        }
    }

    private int samplePendingCells(long gameTime) {
        int limit = ClimoraConfig.server().maxCellSamplesPerTick;
        int created = 0;
        while (created < limit && !pendingCells.isEmpty()) {
            long key = pendingCells.removeFirstLong();
            ClimateCell existing = grid.get(key);
            boolean requested = resampleRequests.remove(key);
            if (!needsSampling(existing) && !requested) {
                continue;
            }
            ClimateCell sampled = BiomeClimateSampler.createCell(level, CellPos.x(key), CellPos.z(key), gameTime);
            if (existing != null) {
                // Re-sampling a cell from an old save: keep its simulated state, only the base values change.
                sampled.copyStateFrom(existing);
            }
            grid.put(sampled);
            created++;
        }
        return created;
    }

    /** Missing cells and cells from saves that lack newer base values are (re-)sampled from the terrain. */
    private static boolean needsSampling(@Nullable ClimateCell cell) {
        return cell == null || !cell.isFullySampled();
    }

    private int updateActiveCells(long gameTime, long deadline) {
        int cellCount = activeCells.length;
        if (cellCount == 0) {
            return 0;
        }

        long dayTime = level.getDayTime();
        int quota = Math.min(cellCount, Mth.positiveCeilDiv(cellCount, FULL_PASS_TICKS));
        int updated = 0;
        for (int i = 0; i < quota; i++) {
            if (System.nanoTime() > deadline) {
                stats.recordDeferred(quota - i);
                break;
            }
            if (cursor >= cellCount) {
                cursor = 0;
            }
            ClimateCell cell = grid.get(activeCells[cursor++]);
            if (cell == null || !cell.isFullySampled()) {
                continue;
            }
            updateCell(cell, gameTime, dayTime);
            updated++;
        }
        return updated;
    }

    private void updateCell(ClimateCell cell, long gameTime, long dayTime) {
        ClimoraConfig.Server config = ClimoraConfig.server();
        long elapsed = Math.max(0L, gameTime - cell.lastUpdateTick());
        double centerX = CellPos.centerBlock(cell.cellX());
        double centerZ = CellPos.centerBlock(cell.cellZ());
        WeatherOverride override = overrideAt(cell.cellX(), cell.cellZ());

        // Pressure: moving weather systems plus local heating.
        float neighbourMean = neighbourMeanTemperature(cell);
        float thermal = Float.isNaN(neighbourMean)
                ? 0.0F : -AtmosphereModel.THERMAL_PRESSURE_PER_DEGREE * (cell.temperature() - neighbourMean);
        float pressure = (float) synoptic.pressure(centerX, centerZ, gameTime) + thermal;
        float lift = override != null ? override.mode().lift : AtmosphereModel.lift(pressure);

        // Wind from the pressure gradient and the drift of the weather systems.
        double gradientX = (pressureAt(cell.cellX() + 1, cell.cellZ(), gameTime)
                - pressureAt(cell.cellX() - 1, cell.cellZ(), gameTime)) / (2.0 * CELL_SIZE_BLOCKS);
        double gradientZ = (pressureAt(cell.cellX(), cell.cellZ() + 1, gameTime)
                - pressureAt(cell.cellX(), cell.cellZ() - 1, gameTime)) / (2.0 * CELL_SIZE_BLOCKS);
        double steeringX = synoptic.driftVelocityX(gameTime) / AtmosphereModel.ADVECTION_BLOCKS_PER_TICK_PER_MPS;
        double steeringZ = synoptic.driftVelocityZ(gameTime) / AtmosphereModel.ADVECTION_BLOCKS_PER_TICK_PER_MPS;
        double gust = 1.0 + 0.8 * cell.storm();
        double windX = (AtmosphereModel.SURFACE_STEERING_SHARE * steeringX - AtmosphereModel.GRADIENT_WIND_FACTOR * gradientX) * gust;
        double windZ = (AtmosphereModel.SURFACE_STEERING_SHARE * steeringZ - AtmosphereModel.GRADIENT_WIND_FACTOR * gradientZ) * gust;
        double windSpeed = Math.sqrt(windX * windX + windZ * windZ);
        if (windSpeed > AtmosphereModel.MAX_WIND_SPEED) {
            windX *= AtmosphereModel.MAX_WIND_SPEED / windSpeed;
            windZ *= AtmosphereModel.MAX_WIND_SPEED / windSpeed;
        }

        // Moisture and clouds arrive from upwind.
        float moisture = cell.moisture();
        float cloud = cell.cloudCover();
        if (!Float.isNaN(moisture) && elapsed > 0) {
            double shiftX = windX * AtmosphereModel.ADVECTION_BLOCKS_PER_TICK_PER_MPS * elapsed;
            double shiftZ = windZ * AtmosphereModel.ADVECTION_BLOCKS_PER_TICK_PER_MPS * elapsed;
            double shift = Math.sqrt(shiftX * shiftX + shiftZ * shiftZ);
            if (shift > CELL_SIZE_BLOCKS) {
                shiftX *= CELL_SIZE_BLOCKS / shift;
                shiftZ *= CELL_SIZE_BLOCKS / shift;
            }
            float[] upwind = ClimateInterpolation.sampleTransported(grid::get, centerX - shiftX, centerZ - shiftZ);
            if (upwind != null) {
                moisture = upwind[0];
                cloud = upwind[1];
            }
        }

        // Temperature: clouds damp the day/night swing, precipitation cools.
        float meanTemperature = meanTemperature(cell, cell.precipitation());
        float diurnalRaw = ClimateModel.diurnalOffset(cell.baseAmplitude(), dayTime);
        float targetTemperature = meanTemperature + diurnalRaw * (1.0F - AtmosphereModel.CLOUD_DIURNAL_DAMPING * cloud);
        if (!Float.isNaN(neighbourMean)) {
            targetTemperature = Mth.lerp(NEIGHBOUR_COUPLING, targetTemperature, neighbourMean);
        }
        float temperature = ClimateModel.relax(cell.temperature(), targetTemperature, elapsed, THERMAL_TIME_CONSTANT_TICKS);

        // Moisture: evaporation towards the equilibrium, drying by precipitation.
        float saturation = (float) AtmosphereModel.saturationMixingRatio(meanTemperature, pressure);
        float equilibrium = AtmosphereModel.equilibriumHumidity(cell.baseHumidity(), cell.waterFraction(), lift)
                + (float) config.humidityBias;
        float moistureTarget = Math.max(equilibrium, 0.05F) * saturation;
        if (Float.isNaN(moisture)) {
            moisture = moistureTarget;
        }
        moisture = ClimateModel.relax(moisture, moistureTarget, elapsed, AtmosphereModel.EVAPORATION_TIME_TICKS);
        moisture *= (float) Math.exp(-elapsed * cell.precipitation() / AtmosphereModel.PRECIPITATION_DRYING_TICKS);
        if (override != null) {
            // Move the moisture towards what the forced weather needs, so it continues naturally afterwards.
            float forced = override.mode().humidity * saturation;
            moisture = override.mode() == WeatherOverride.Mode.CLEAR ? Math.min(moisture, forced) : Math.max(moisture, forced);
        }
        float columnHumidity = moisture / saturation;
        // Near the ground, air cooler than the daily mean would be supersaturated: the excess forms dew and fog.
        float surfaceHumidity = Math.min(1.0F, AtmosphereModel.relativeHumidity(moisture, temperature, pressure));

        // Clouds, precipitation and thunderstorms.
        float cloudTarget;
        float precipitationTarget;
        float cloudTime = AtmosphereModel.CLOUD_TIME_TICKS;
        float precipitationTime = AtmosphereModel.PRECIPITATION_TIME_TICKS;
        float stormTime = AtmosphereModel.STORM_TIME_TICKS;
        if (override != null) {
            cloudTarget = override.mode().cloudCover;
            precipitationTarget = override.mode().precipitation;
            cloudTime = precipitationTime = stormTime = WeatherOverride.RESPONSE_TIME_TICKS;
        } else {
            cloudTarget = AtmosphereModel.cloudTarget(columnHumidity, lift);
            precipitationTarget = AtmosphereModel.precipitationTarget(columnHumidity, lift);
        }
        cloud = ClimateModel.relax(cloud, cloudTarget, elapsed, cloudTime);
        float precipitation = ClimateModel.relax(cell.precipitation(), precipitationTarget, elapsed, precipitationTime);
        // Clouds and rain are separate fields with their own response times, so without this the cell could
        // rain out of a sky the cloud formula had only half filled, and the player saw rain from a blue gap.
        cloud = Math.max(cloud, AtmosphereModel.overcastFor(precipitation));

        float heating = cell.baseAmplitude() > 0.0F ? Math.max(0.0F, diurnalRaw / cell.baseAmplitude()) : 0.0F;
        float stormTarget;
        if (override != null) {
            stormTarget = config.thunderstorms ? override.mode().storm : 0.0F;
        } else {
            stormTarget = config.thunderstorms
                    ? AtmosphereModel.stormTarget(temperature, columnHumidity, lift, precipitation, heating) : 0.0F;
        }
        float storm = ClimateModel.relax(cell.storm(), stormTarget, elapsed, stormTime);

        cell.setTemperature(temperature);
        cell.setWeather(moisture, AtmosphereModel.clamp01(cloud), AtmosphereModel.clamp01(precipitation), AtmosphereModel.clamp01(storm));
        cell.setDerived(pressure, surfaceHumidity, (float) windX, (float) windZ, gameTime);
        cell.setLastUpdateTick(gameTime);
    }

    /**
     * Daily mean temperature of a cell. The height of the cell is already included in its base
     * temperature (the biome profile is applied at the sampled height), so only precipitation cools it.
     */
    private float meanTemperature(ClimateCell cell, float precipitation) {
        return cell.baseTemperature() - AtmosphereModel.PRECIPITATION_COOLING * precipitation;
    }

    /** Current pressure of a cell, or the synoptic pressure at its center if it has not been updated recently. */
    private double pressureAt(int cellX, int cellZ, long gameTime) {
        ClimateCell cell = grid.get(CellPos.key(cellX, cellZ));
        if (cell != null && gameTime - cell.derivedTick() <= STALE_PRESSURE_TICKS) {
            return cell.pressure();
        }
        return synoptic.pressure(CellPos.centerBlock(cellX), CellPos.centerBlock(cellZ), gameTime);
    }

    /** Mean temperature of the existing orthogonal neighbours, or NaN if there are none. */
    private float neighbourMeanTemperature(ClimateCell cell) {
        float sum = 0.0F;
        int count = 0;
        long[] neighbours = {
                CellPos.key(cell.cellX() + 1, cell.cellZ()),
                CellPos.key(cell.cellX() - 1, cell.cellZ()),
                CellPos.key(cell.cellX(), cell.cellZ() + 1),
                CellPos.key(cell.cellX(), cell.cellZ() - 1)
        };
        for (long key : neighbours) {
            ClimateCell neighbour = grid.get(key);
            if (neighbour != null) {
                sum += neighbour.temperature();
                count++;
            }
        }
        return count == 0 ? Float.NaN : sum / count;
    }
}
