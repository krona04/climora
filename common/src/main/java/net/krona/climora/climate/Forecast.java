package net.krona.climora.climate;

import net.krona.climora.config.ClimoraConfig;
import net.krona.climora.weather.LocalWeather;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Weather forecast for a place, for {@code /climora forecast} and later the barometer.
 * <p>
 * The pressure part is exact: the weather systems are a pure function of position and weather time, so
 * the pressure over a place in six hours is known now. The moisture part is an estimate: the air that will
 * be here is upwind now, so it is taken from there and let evaporate towards what this place holds at the
 * forecast pressure. Clouds, rain and storms then follow from the same formulas the simulation uses.
 */
public final class Forecast {
    /** Forecast horizons, in-game hours. 0 is the weather right now. */
    private static final int[] HOURS = {0, 1, 3, 6, 12};
    private static final int TICKS_PER_HOUR = 1000;

    public enum Sky {
        CLEAR, PARTLY_CLOUDY, CLOUDY, OVERCAST
    }

    public enum Chance {
        NONE, POSSIBLE, LIKELY, NOW
    }

    /**
     * @param pressure     sea-level pressure, hPa
     * @param type         what precipitation would fall as, meaningful when {@code precipitation} is not NONE
     */
    public record Entry(int hours, float pressure, Sky sky, Chance precipitation,
                        AtmosphereModel.PrecipitationType type, boolean thunderstorms) {
    }

    private Forecast() {
    }

    /** Empty when the cell of the position is not simulated yet. */
    public static List<Entry> forecast(ClimateSimulation simulation, BlockPos pos) {
        ClimateCell cell = simulation.grid().get(CellPos.key(CellPos.blockToCell(pos.getX()), CellPos.blockToCell(pos.getZ())));
        if (cell == null || !cell.isFullySampled() || Float.isNaN(cell.moisture())) {
            return List.of();
        }
        ClimoraConfig.Server config = ClimoraConfig.server();
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        double weatherTime = simulation.weatherTime();
        // Local heating shifts the pressure of the cell a little from the weather systems; assume it stays.
        float thermal = cell.pressure() - (float) simulation.synoptic().pressure(x, z, weatherTime);
        long dayTime = simulation.level().getDayTime();

        List<Entry> entries = new ArrayList<>(HOURS.length);
        for (int hours : HOURS) {
            if (hours == 0) {
                entries.add(now(simulation, cell, pos));
                continue;
            }
            double weatherTicks = hours * TICKS_PER_HOUR * config.weatherSpeed;
            float pressure = (float) simulation.synoptic().pressure(x, z, weatherTime + weatherTicks) + thermal;
            float lift = AtmosphereModel.lift(pressure);

            // The air that will be here is upwind now.
            double shift = AtmosphereModel.ADVECTION_BLOCKS_PER_TICK_PER_MPS * weatherTicks;
            ClimateCell upwind = simulation.grid().get(CellPos.key(
                    CellPos.blockToCell((int) Math.floor(x - cell.windX() * shift)),
                    CellPos.blockToCell((int) Math.floor(z - cell.windZ() * shift))));
            float arriving = upwind != null && !Float.isNaN(upwind.moisture()) ? upwind.moisture() : cell.moisture();

            // On the way it evaporates towards what this place holds at that pressure.
            float mean = AtmosphereModel.columnEquilibrium(cell.baseTemperature(), 0.0F);
            float saturation = (float) AtmosphereModel.saturationMixingRatio(mean, pressure);
            float equilibrium = AtmosphereModel.equilibriumHumidity(cell.baseHumidity(), cell.waterFraction(), lift)
                    + (float) config.humidityBias;
            float moisture = ClimateModel.relax(arriving, Math.max(equilibrium, 0.05F) * saturation, weatherTicks,
                    AtmosphereModel.EVAPORATION_TIME_TICKS);
            float humidity = moisture / saturation;

            float precipitation = AtmosphereModel.precipitationTarget(humidity, lift);
            float cloud = Math.max(AtmosphereModel.cloudTarget(humidity, lift), AtmosphereModel.overcastFor(precipitation));
            float diurnal = ClimateModel.diurnalOffset(cell.baseAmplitude(), dayTime + (long) hours * TICKS_PER_HOUR);
            float temperature = mean + diurnal * (1.0F - AtmosphereModel.CLOUD_DIURNAL_DAMPING * cloud);
            float heating = cell.baseAmplitude() > 0.0F ? Math.max(0.0F, diurnal / cell.baseAmplitude()) : 0.0F;
            float storm = config.thunderstorms
                    ? AtmosphereModel.stormTarget(temperature, humidity, lift, precipitation, heating) : 0.0F;
            float surface = temperature + ClimateModel.altitudeOffset(cell.elevation(), pos.getY());
            AtmosphereModel.PrecipitationType type = AtmosphereModel.precipitationType(surface);

            Chance chance = precipitation >= 0.3F ? Chance.LIKELY
                    : precipitation >= LocalWeather.PRECIPITATION_THRESHOLD ? Chance.POSSIBLE : Chance.NONE;
            entries.add(new Entry(hours, pressure, sky(cloud), chance, type,
                    chance != Chance.NONE && storm >= LocalWeather.THUNDERSTORM));
        }
        return entries;
    }

    private static Entry now(ClimateSimulation simulation, ClimateCell cell, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        boolean falling = simulation.precipitation(x, z) >= LocalWeather.PRECIPITATION_THRESHOLD;
        return new Entry(0, cell.pressure(), sky(cell.cloudCover()), falling ? Chance.NOW : Chance.NONE,
                LocalWeather.precipitationType(simulation, x, pos.getY(), z),
                falling && simulation.storm(x, z) >= LocalWeather.THUNDERSTORM);
    }

    private static Sky sky(float cloud) {
        if (cloud < 0.2F) {
            return Sky.CLEAR;
        }
        if (cloud < 0.5F) {
            return Sky.PARTLY_CLOUDY;
        }
        return cloud < 0.85F ? Sky.CLOUDY : Sky.OVERCAST;
    }
}
