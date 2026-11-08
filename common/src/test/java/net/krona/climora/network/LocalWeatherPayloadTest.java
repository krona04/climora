package net.krona.climora.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalWeatherPayloadTest {
    private static LocalWeatherPayload roundTrip(LocalWeatherPayload payload) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        LocalWeatherPayload.CODEC.encode(buf, payload);
        return LocalWeatherPayload.CODEC.decode(buf);
    }

    @Test
    void weatherOfEveryCellArrives() {
        LocalWeatherPayload.Cell cell = new LocalWeatherPayload.Cell(true, -3.4F, 72, 0.9F, 1.0F, 0.6F, 0.2F, 4.0F, -2.0F,
                6.3F, 0.5F, 0.75F, -9.0F, 3.0F);
        LocalWeatherPayload sent = new LocalWeatherPayload(1000L, -2, 5, 2, 12.5, -7.25, 0.08F, -0.01F,
                new LocalWeatherPayload.Cell[]{cell, LocalWeatherPayload.Cell.MISSING, cell, cell});

        LocalWeatherPayload received = roundTrip(sent);
        LocalWeatherPayload.Cell first = received.cells()[0];

        assertEquals(2, received.size());
        assertFalse(received.cells()[1].present());
        assertEquals(-3.4F, first.temperature(), 0.05F);
        assertEquals(0.6F, first.precipitation(), 0.01F);
        assertEquals(6.3F, first.columnTemperature(), 0.05F);
        assertEquals(0.5F, first.hail(), 0.01F);
        assertEquals(0.75F, first.fog(), 0.01F);
        assertEquals(-9.0F, first.baseTemperature(), 0.05F);
        assertEquals(3.0F, first.baseAmplitude(), 0.05F);
    }

    @Test
    void unknownAirAloftStaysUnknown() {
        LocalWeatherPayload.Cell cell = new LocalWeatherPayload.Cell(true, 5.0F, 64, 0.5F, 0.2F, 0.0F, 0.0F, 0.0F, 0.0F,
                Float.NaN, 0.0F, 0.0F, Float.NaN, Float.NaN);
        LocalWeatherPayload received = roundTrip(new LocalWeatherPayload(1L, 0, 0, 1, 0.0, 0.0, 0.0F, 0.0F,
                new LocalWeatherPayload.Cell[]{cell}));

        assertTrue(Float.isNaN(received.cells()[0].columnTemperature()),
                "freezing rain switched off on the server sends no air aloft");
        assertTrue(Float.isNaN(received.cells()[0].baseTemperature()));
        assertTrue(Float.isNaN(received.cells()[0].baseAmplitude()));
    }
}
