package net.krona.climora.block;

import net.krona.climora.climate.AtmosphereModel;
import net.krona.climora.climate.ClimateInterpolation;
import net.krona.climora.climate.ClimateManager;
import net.krona.climora.climate.ClimateSimulation;
import net.krona.climora.registry.ClimoraRegistries;
import net.krona.climora.weather.LocalWeather;
import net.krona.climora.weather.WeatherView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class WeatherVaneBlockEntity extends BlockEntity {
    private static final int SIGNAL_INTERVAL_TICKS = 20;

    /** Comparator output, 0..15, from the wind strength on the Beaufort scale. Server side. */
    private int signal;

    // Client-side animation of the arrow, radians. The arrow points to where the wind comes from.
    private float angle = Float.NaN;
    private float previousAngle;
    private float angularVelocity;

    public WeatherVaneBlockEntity(BlockPos pos, BlockState state) {
        super(ClimoraRegistries.WEATHER_VANE_ENTITY.get(), pos, state);
    }

    public int signal() {
        return signal;
    }

    public float angle(float partialTick) {
        if (Float.isNaN(angle)) {
            return 0.0F;
        }
        return previousAngle + Mth.wrapDegrees((angle - previousAngle) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * partialTick;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WeatherVaneBlockEntity vane) {
        if ((level.getGameTime() + pos.asLong()) % SIGNAL_INTERVAL_TICKS != 0 || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int newSignal = 0;
        ClimateSimulation simulation = ClimateManager.get(serverLevel);
        if (simulation != null) {
            ClimateInterpolation.Sample sample = simulation.sample(pos.getX() + 0.5, pos.getZ() + 0.5);
            if (sample != null) {
                float speed = AtmosphereModel.windAtHeight(sample.windSpeed(), pos.getY() + 1 - sample.surfaceElevation());
                newSignal = Math.round(AtmosphereModel.beaufort(speed) * 15.0F / 12.0F);
            }
        }
        if (newSignal != vane.signal) {
            vane.signal = newSignal;
            vane.setChanged();
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, WeatherVaneBlockEntity vane) {
        WeatherView view = LocalWeather.view(level);
        float windX = view != null ? view.windX(pos.getX() + 0.5, pos.getZ() + 0.5) : 0.0F;
        float windZ = view != null ? view.windZ(pos.getX() + 0.5, pos.getZ() + 0.5) : 0.0F;
        float speed = (float) Math.sqrt(windX * windX + windZ * windZ);
        RandomSource random = level.getRandom();

        if (Float.isNaN(vane.angle)) {
            vane.angle = speed > 0.1F ? (float) Math.atan2(-windZ, -windX) : random.nextFloat() * Mth.TWO_PI;
            vane.previousAngle = vane.angle;
        }
        vane.previousAngle = vane.angle;

        if (speed > 0.1F) {
            float target = (float) Math.atan2(-windZ, -windX);
            float difference = Mth.wrapDegrees((target - vane.angle) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            // A stronger wind pushes harder; gusts make the arrow wobble.
            float stiffness = 0.02F + Math.min(speed, 20.0F) * 0.004F;
            vane.angularVelocity += difference * stiffness + (random.nextFloat() - 0.5F) * speed * 0.003F;
        }
        vane.angularVelocity *= 0.88F;
        vane.angle += vane.angularVelocity;
        vane.angle = Mth.wrapDegrees(vane.angle * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        signal = tag.getInt("Signal");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Signal", signal);
    }
}
