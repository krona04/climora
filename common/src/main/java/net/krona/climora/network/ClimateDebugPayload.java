package net.krona.climora.network;

import net.krona.climora.Climora;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: climate around the player for the F3 debug screen. Values are interpolated at the
 * player's position, except the {@code cell*} values which describe the cell the player stands in.
 *
 * @param simulated          whether the player's dimension has a climate simulation
 * @param cellExists         whether the player's cell has been sampled yet
 * @param airTemperature     at the player's feet, °C (NaN if no cells nearby)
 * @param surfaceElevation   interpolated surface height (NaN if unknown)
 * @param precipitationType  ordinal of {@code AtmosphereModel.PrecipitationType} at the player's feet
 * @param forcedWeather      ordinal + 1 of {@code WeatherOverride.Mode} forced in the player's cell, 0 if none
 */
public record ClimateDebugPayload(
        boolean simulated,
        int cellX,
        int cellZ,
        boolean cellExists,
        float airTemperature,
        float surfaceElevation,
        float pressure,
        float humidity,
        float cloudCover,
        float precipitation,
        int precipitationType,
        float storm,
        float windX,
        float windZ,
        float cellTemperature,
        float targetTemperature,
        float baseTemperature,
        int forcedWeather,
        int activeCells,
        float averageTickMillis
) implements CustomPacketPayload {
    public static final Type<ClimateDebugPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Climora.MOD_ID, "climate_debug"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClimateDebugPayload> CODEC =
            StreamCodec.of(ClimateDebugPayload::write, ClimateDebugPayload::read);

    public static ClimateDebugPayload notSimulated() {
        return new ClimateDebugPayload(false, 0, 0, false, Float.NaN, Float.NaN, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0);
    }

    private static void write(RegistryFriendlyByteBuf buf, ClimateDebugPayload p) {
        buf.writeBoolean(p.simulated);
        buf.writeVarInt(p.cellX);
        buf.writeVarInt(p.cellZ);
        buf.writeBoolean(p.cellExists);
        buf.writeFloat(p.airTemperature);
        buf.writeFloat(p.surfaceElevation);
        buf.writeFloat(p.pressure);
        buf.writeFloat(p.humidity);
        buf.writeFloat(p.cloudCover);
        buf.writeFloat(p.precipitation);
        buf.writeVarInt(p.precipitationType);
        buf.writeFloat(p.storm);
        buf.writeFloat(p.windX);
        buf.writeFloat(p.windZ);
        buf.writeFloat(p.cellTemperature);
        buf.writeFloat(p.targetTemperature);
        buf.writeFloat(p.baseTemperature);
        buf.writeVarInt(p.forcedWeather);
        buf.writeVarInt(p.activeCells);
        buf.writeFloat(p.averageTickMillis);
    }

    private static ClimateDebugPayload read(RegistryFriendlyByteBuf buf) {
        return new ClimateDebugPayload(
                buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readVarInt(), buf.readVarInt(), buf.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
