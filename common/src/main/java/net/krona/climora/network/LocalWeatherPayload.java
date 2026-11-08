package net.krona.climora.network;

import net.krona.climora.Climora;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client, every second: weather of the cells around the player, for rendering and sky effects.
 * A payload with {@code size == 0} tells the client to use vanilla weather.
 * <p>
 * Cells are listed row by row: index {@code dz * size + dx} for cell {@code (originX + dx, originZ + dz)}.
 * Values are quantised to bytes, about 17 bytes per cell.
 *
 * @param gameTime      server game time when the payload was built
 * @param originX       cell X of the first column
 * @param originZ       cell Z of the first row
 * @param size          number of cells per side
 * @param cloudOffsetX  how far the weather systems have drifted since the world was created, blocks
 * @param cloudOffsetZ  see {@code cloudOffsetX}
 * @param driftX        current drift velocity of the weather systems, blocks per game tick (includes weatherSpeed)
 * @param driftZ        see {@code driftX}
 */
public record LocalWeatherPayload(
        long gameTime,
        int originX,
        int originZ,
        int size,
        double cloudOffsetX,
        double cloudOffsetZ,
        float driftX,
        float driftZ,
        Cell[] cells
) implements CustomPacketPayload {
    public static final Type<LocalWeatherPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Climora.MOD_ID, "local_weather"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LocalWeatherPayload> CODEC =
            StreamCodec.of(LocalWeatherPayload::write, LocalWeatherPayload::read);

    /** Largest accepted side length, to reject malformed packets. */
    private static final int MAX_SIZE = 33;

    /**
     * Weather of one cell. {@code present == false} means the server has not sampled it yet.
     *
     * @param windX             near-ground wind, m/s
     * @param columnTemperature air aloft at the cell elevation, °C, or NaN if unknown
     * @param baseTemperature   daily mean of the cell from its biomes, °C: with {@code baseAmplitude} the
     *                          client corrects the cell average for the biome at a position, as the server does
     */
    public record Cell(boolean present, float temperature, int elevation, float humidity, float cloudCover,
                       float precipitation, float storm, float windX, float windZ, float columnTemperature,
                       float hail, float fog, float baseTemperature, float baseAmplitude) {
        public static final Cell MISSING = new Cell(false, 0.0F, 0, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, Float.NaN, 0.0F, 0.0F,
                Float.NaN, Float.NaN);
    }

    /** Short value that marks an unknown temperature; real ones stay within ±3000 (±300 °C). */
    private static final short UNKNOWN_TEMPERATURE = Short.MIN_VALUE;

    public static LocalWeatherPayload inactive() {
        return new LocalWeatherPayload(0L, 0, 0, 0, 0.0, 0.0, 0.0F, 0.0F, new Cell[0]);
    }

    public boolean isActive() {
        return size > 0;
    }

    private static void write(RegistryFriendlyByteBuf buf, LocalWeatherPayload payload) {
        buf.writeLong(payload.gameTime);
        buf.writeVarInt(payload.originX);
        buf.writeVarInt(payload.originZ);
        buf.writeByte(payload.size);
        buf.writeDouble(payload.cloudOffsetX);
        buf.writeDouble(payload.cloudOffsetZ);
        buf.writeFloat(payload.driftX);
        buf.writeFloat(payload.driftZ);
        for (Cell cell : payload.cells) {
            buf.writeBoolean(cell.present);
            if (!cell.present) {
                continue;
            }
            buf.writeShort(Math.round(cell.temperature * 10.0F));
            buf.writeShort(cell.elevation);
            buf.writeByte(unit(cell.humidity / 1.25F));
            buf.writeByte(unit(cell.cloudCover));
            buf.writeByte(unit(cell.precipitation));
            buf.writeByte(unit(cell.storm));
            buf.writeByte(Math.max(-127, Math.min(127, Math.round(cell.windX * 3.0F))));
            buf.writeByte(Math.max(-127, Math.min(127, Math.round(cell.windZ * 3.0F))));
            buf.writeShort(Float.isNaN(cell.columnTemperature) ? UNKNOWN_TEMPERATURE
                    : Math.max(-3000, Math.min(3000, Math.round(cell.columnTemperature * 10.0F))));
            buf.writeByte(unit(cell.hail));
            buf.writeByte(unit(cell.fog));
            buf.writeShort(Float.isNaN(cell.baseTemperature) ? UNKNOWN_TEMPERATURE
                    : Math.max(-3000, Math.min(3000, Math.round(cell.baseTemperature * 10.0F))));
            buf.writeByte(Float.isNaN(cell.baseAmplitude) ? 255 : Math.max(0, Math.min(254, Math.round(cell.baseAmplitude * 10.0F))));
        }
    }

    private static LocalWeatherPayload read(RegistryFriendlyByteBuf buf) {
        long gameTime = buf.readLong();
        int originX = buf.readVarInt();
        int originZ = buf.readVarInt();
        int size = buf.readUnsignedByte();
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("Local weather payload too large: " + size);
        }
        double cloudOffsetX = buf.readDouble();
        double cloudOffsetZ = buf.readDouble();
        float driftX = buf.readFloat();
        float driftZ = buf.readFloat();
        Cell[] cells = new Cell[size * size];
        for (int i = 0; i < cells.length; i++) {
            if (!buf.readBoolean()) {
                cells[i] = Cell.MISSING;
                continue;
            }
            float temperature = buf.readShort() / 10.0F;
            int elevation = buf.readShort();
            float humidity = buf.readUnsignedByte() / 255.0F * 1.25F;
            float cloudCover = buf.readUnsignedByte() / 255.0F;
            float precipitation = buf.readUnsignedByte() / 255.0F;
            float storm = buf.readUnsignedByte() / 255.0F;
            float windX = buf.readByte() / 3.0F;
            float windZ = buf.readByte() / 3.0F;
            short column = buf.readShort();
            float hail = buf.readUnsignedByte() / 255.0F;
            float fog = buf.readUnsignedByte() / 255.0F;
            short base = buf.readShort();
            int amplitude = buf.readUnsignedByte();
            cells[i] = new Cell(true, temperature, elevation, humidity, cloudCover, precipitation, storm, windX, windZ,
                    column == UNKNOWN_TEMPERATURE ? Float.NaN : column / 10.0F, hail, fog,
                    base == UNKNOWN_TEMPERATURE ? Float.NaN : base / 10.0F, amplitude == 255 ? Float.NaN : amplitude / 10.0F);
        }
        return new LocalWeatherPayload(gameTime, originX, originZ, size, cloudOffsetX, cloudOffsetZ, driftX, driftZ, cells);
    }

    private static int unit(float value) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
