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
 * Values are quantised to bytes, about 10 bytes per cell.
 *
 * @param gameTime      server game time when the payload was built
 * @param originX       cell X of the first column
 * @param originZ       cell Z of the first row
 * @param size          number of cells per side
 * @param cloudOffsetX  how far the weather systems have drifted since the world was created, blocks
 * @param cloudOffsetZ  see {@code cloudOffsetX}
 * @param driftX        current drift velocity of the weather systems, blocks per tick
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
     * @param windX near-ground wind, m/s
     */
    public record Cell(boolean present, float temperature, int elevation, float humidity, float cloudCover,
                       float precipitation, float storm, float windX, float windZ) {
        public static final Cell MISSING = new Cell(false, 0.0F, 0, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

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
            cells[i] = new Cell(true,
                    buf.readShort() / 10.0F,
                    buf.readShort(),
                    buf.readUnsignedByte() / 255.0F * 1.25F,
                    buf.readUnsignedByte() / 255.0F,
                    buf.readUnsignedByte() / 255.0F,
                    buf.readUnsignedByte() / 255.0F,
                    buf.readByte() / 3.0F,
                    buf.readByte() / 3.0F);
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
