package net.krona.climora.climate;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.krona.climora.config.ClimoraConfig;
import net.krona.climora.weather.HailDamage;
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
    /** Surges of storm updrafts, which decide where and when a storm that can hail does. */
    private final GradientNoise hailSurges;
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
        this.hailSurges = new GradientNoise(level.getSeed() ^ 0x4A11L);
        this.lightning = new LightningSpawner(level);
        if (Double.isNaN(grid.weatherTime())) {
            // New data, or data from 0.1: its pressure pattern ran on game time, so continue from there.
            grid.setWeatherTime(level.getGameTime());
        }
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

    /** Clock of the weather systems: game time scaled by {@code weatherSpeed}, ticks. */
    public double weatherTime() {
        return grid.weatherTime();
    }

    /** How far the weather systems move per game tick, blocks. X component. */
    public double driftPerGameTickX() {
        return synoptic.driftVelocityX(weatherTime()) * ClimoraConfig.server().weatherSpeed;
    }

    /** How far the weather systems move per game tick, blocks. Z component. */
    public double driftPerGameTickZ() {
        return synoptic.driftVelocityZ(weatherTime()) * ClimoraConfig.server().weatherSpeed;
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
    public float wetness(double x, double z) {
        float value = ClimateInterpolation.field(grid::get, x, z, ClimateInterpolation.Field.WETNESS);
        return Float.isNaN(value) ? 0.0F : value;
    }

    @Override
    public float hail(double x, double z) {
        float value = ClimateInterpolation.field(grid::get, x, z, ClimateInterpolation.Field.HAIL);
        return Float.isNaN(value) ? 0.0F : value;
    }

    @Override
    public float columnTemperature(double x, double y, double z) {
        if (!ClimoraConfig.server().freezingRain) {
            // Unknown air aloft leaves plain rain and snow: freezing rain is switched off.
            return Float.NaN;
        }
        ClimateInterpolation.Sample sample = sample(x, z);
        // The same biome correction as the ground air: a snowfield inside a mild cell is cold all the way up,
        // or every shower at its edge would fall as freezing rain.
        return sample != null ? sample.columnTemperature(y) + microclimate(sample, x, y, z) : Float.NaN;
    }

    /** Correction of the cell-blended temperature for the biome at a position, see {@link Microclimate}. */
    public float microclimate(ClimateInterpolation.Sample sample, double x, double y, double z) {
        return Microclimate.offset(level, x, y, z, sample.baseTemperature(), sample.baseAmplitude(),
                sample.surfaceElevation(), sample.cloudCover(), level.getDayTime());
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
        return sample != null ? sample.airTemperature(y) + microclimate(sample, x, y, z) : Float.NaN;
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
        grid.setWeatherTime(grid.weatherTime() + ClimoraConfig.server().weatherSpeed);

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
        HailDamage.tick(this);

        long end = System.nanoTime();
        stats.recordTick(end - start, created, updated, end > deadline);
    }

    /** Temperature the cell tends to right now, with clouds, precipitation and neighbours. */
    public float targetTemperature(ClimateCell cell) {
        float meanTemperature = AtmosphereModel.columnEquilibrium(cell.baseTemperature(), cell.precipitation());
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
        // Weather processes run on the weather clock; temperature and the day keep game time.
        double weatherElapsed = elapsed * config.weatherSpeed;
        double weatherTime = grid.weatherTime();
        double centerX = CellPos.centerBlock(cell.cellX());
        double centerZ = CellPos.centerBlock(cell.cellZ());
        WeatherOverride override = overrideAt(cell.cellX(), cell.cellZ());

        // Pressure: moving weather systems plus local heating.
        float neighbourMean = neighbourMeanTemperature(cell);
        float thermal = Float.isNaN(neighbourMean)
                ? 0.0F : -AtmosphereModel.THERMAL_PRESSURE_PER_DEGREE * (cell.temperature() - neighbourMean);
        float pressure = (float) synoptic.pressure(centerX, centerZ, weatherTime) + thermal;
        float lift = override != null ? override.mode().lift : AtmosphereModel.lift(pressure);

        // Wind from the pressure gradient and the drift of the weather systems.
        double gradientX = (pressureAt(cell.cellX() + 1, cell.cellZ(), gameTime, weatherTime)
                - pressureAt(cell.cellX() - 1, cell.cellZ(), gameTime, weatherTime)) / (2.0 * CELL_SIZE_BLOCKS);
        double gradientZ = (pressureAt(cell.cellX(), cell.cellZ() + 1, gameTime, weatherTime)
                - pressureAt(cell.cellX(), cell.cellZ() - 1, gameTime, weatherTime)) / (2.0 * CELL_SIZE_BLOCKS);
        // Drift per weather tick, so the steering wind in m/s does not change with weatherSpeed.
        double steeringX = synoptic.driftVelocityX(weatherTime) / AtmosphereModel.ADVECTION_BLOCKS_PER_TICK_PER_MPS;
        double steeringZ = synoptic.driftVelocityZ(weatherTime) / AtmosphereModel.ADVECTION_BLOCKS_PER_TICK_PER_MPS;
        double gust = 1.0 + 0.8 * cell.storm();
        double windX = (AtmosphereModel.SURFACE_STEERING_SHARE * steeringX - AtmosphereModel.GRADIENT_WIND_FACTOR * gradientX) * gust;
        double windZ = (AtmosphereModel.SURFACE_STEERING_SHARE * steeringZ - AtmosphereModel.GRADIENT_WIND_FACTOR * gradientZ) * gust;
        if (override != null) {
            windX *= override.mode().windScale;
            windZ *= override.mode().windScale;
        }
        double windSpeed = Math.sqrt(windX * windX + windZ * windZ);
        if (windSpeed > AtmosphereModel.MAX_WIND_SPEED) {
            windX *= AtmosphereModel.MAX_WIND_SPEED / windSpeed;
            windZ *= AtmosphereModel.MAX_WIND_SPEED / windSpeed;
        }

        // Moisture, clouds and the air aloft arrive from upwind.
        float moisture = cell.moisture();
        float cloud = cell.cloudCover();
        float column = cell.columnTemperature();
        if (!Float.isNaN(moisture) && elapsed > 0) {
            double shiftX = windX * AtmosphereModel.ADVECTION_BLOCKS_PER_TICK_PER_MPS * weatherElapsed;
            double shiftZ = windZ * AtmosphereModel.ADVECTION_BLOCKS_PER_TICK_PER_MPS * weatherElapsed;
            double shift = Math.sqrt(shiftX * shiftX + shiftZ * shiftZ);
            if (shift > CELL_SIZE_BLOCKS) {
                shiftX *= CELL_SIZE_BLOCKS / shift;
                shiftZ *= CELL_SIZE_BLOCKS / shift;
            }
            float[] upwind = ClimateInterpolation.sampleTransported(grid::get, centerX - shiftX, centerZ - shiftZ);
            if (upwind != null) {
                moisture = upwind[0];
                cloud = upwind[1];
                if (!Float.isNaN(upwind[2])) {
                    column = upwind[2];
                }
            }
        }

        // Temperature: clouds damp the day/night swing, precipitation cools.
        float meanTemperature = AtmosphereModel.columnEquilibrium(cell.baseTemperature(), cell.precipitation());
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
        moisture = ClimateModel.relax(moisture, moistureTarget, weatherElapsed, AtmosphereModel.EVAPORATION_TIME_TICKS);
        moisture *= (float) Math.exp(-weatherElapsed * cell.precipitation() / AtmosphereModel.PRECIPITATION_DRYING_TICKS);
        if (override != null) {
            // Move the moisture towards what the forced weather needs, so it continues naturally afterwards.
            float forced = override.mode().humidity * saturation;
            moisture = override.mode() == WeatherOverride.Mode.CLEAR ? Math.min(moisture, forced) : Math.max(moisture, forced);
        }
        float columnHumidity = moisture / saturation;
        // Near the ground, air cooler than the daily mean would be supersaturated: the excess forms dew and fog.
        float rawSurfaceHumidity = AtmosphereModel.relativeHumidity(moisture, temperature, pressure);
        float surfaceHumidity = Math.min(1.0F, rawSurfaceHumidity);

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
        cloud = ClimateModel.relax(cloud, cloudTarget, weatherElapsed, cloudTime);
        float precipitation = ClimateModel.relax(cell.precipitation(), precipitationTarget, weatherElapsed, precipitationTime);
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
        float storm = ClimateModel.relax(cell.storm(), stormTarget, weatherElapsed, stormTime);
        float hail = 0.0F;
        if (config.hail && config.thunderstorms) {
            if (override != null && override.mode().forcesHail) {
                hail = temperature > 0.0F ? AtmosphereModel.HAIL_MAX : 0.0F;
            } else {
                float surge = AtmosphereModel.hailSurge(hailSurges.sample(cell.cellX() * AtmosphereModel.HAIL_SURGE_PER_CELL,
                        cell.cellZ() * AtmosphereModel.HAIL_SURGE_PER_CELL, weatherTime / AtmosphereModel.HAIL_SURGE_TICKS));
                float dayHeat = cell.baseTemperature() + Math.max(0.0F, diurnalRaw);
                hail = AtmosphereModel.hailTarget(storm, lift, dayHeat, surge);
            }
        }

        // Air aloft: carried by the wind (above), then slowly takes on the climate of the ground under it.
        float columnTarget = AtmosphereModel.columnEquilibrium(cell.baseTemperature(), precipitation);
        if (Float.isNaN(column)) {
            column = columnTarget;
        }
        column = ClimateModel.relax(column, columnTarget, weatherElapsed, AtmosphereModel.COLUMN_ADJUST_TICKS);
        if (override != null && !Float.isNaN(override.mode().columnWarming)) {
            column = Math.max(column, Math.max(temperature + override.mode().columnWarming, WeatherOverride.WARM_ALOFT));
        }

        // Wet ground: soaked by rain, dried by warm, dry, windy air. Snow does not wet it until it melts.
        float groundWind = (float) Math.sqrt(windX * windX + windZ * windZ);
        float wetnessTarget = temperature > AtmosphereModel.SNOW_TEMPERATURE ? AtmosphereModel.wetnessTarget(precipitation) : 0.0F;
        float wetness = cell.wetness();
        float wetnessTime = wetnessTarget > wetness
                ? AtmosphereModel.WETTING_TIME_TICKS
                : AtmosphereModel.dryingTime(temperature, surfaceHumidity, groundWind);
        wetness = ClimateModel.relax(wetness, wetnessTarget, weatherElapsed, wetnessTime);

        cell.setTemperature(temperature);
        cell.setWeather(moisture, AtmosphereModel.clamp01(cloud), AtmosphereModel.clamp01(precipitation), AtmosphereModel.clamp01(storm));
        cell.setGround(column, AtmosphereModel.clamp01(wetness));
        cell.setHail(hail, AtmosphereModel.fog(rawSurfaceHumidity, groundWind, cloud, precipitation));
        cell.setDerived(pressure, surfaceHumidity, (float) windX, (float) windZ, gameTime);
        cell.setLastUpdateTick(gameTime);

        boolean raining = precipitation >= AtmosphereModel.OVERCAST_PRECIPITATION;
        stats.recordWeather(elapsed, raining, raining && storm >= LightningSpawner.MIN_STORM, raining && hail >= HAIL_THRESHOLD,
                raining && AtmosphereModel.precipitationType(temperature, column) == AtmosphereModel.PrecipitationType.FREEZING_RAIN);
    }

    /** Hail below this intensity is not drawn and does not count as hail. */
    public static final float HAIL_THRESHOLD = 0.08F;

    /** Current pressure of a cell, or the synoptic pressure at its center if it has not been updated recently. */
    private double pressureAt(int cellX, int cellZ, long gameTime, double weatherTime) {
        ClimateCell cell = grid.get(CellPos.key(cellX, cellZ));
        if (cell != null && gameTime - cell.derivedTick() <= STALE_PRESSURE_TICKS) {
            return cell.pressure();
        }
        return synoptic.pressure(CellPos.centerBlock(cellX), CellPos.centerBlock(cellZ), weatherTime);
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
