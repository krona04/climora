package net.krona.climora.network;

import net.krona.climora.Climora;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: start or stop sending {@link ClimateDebugPayload}. Sent when the F3 screen opens or closes.
 */
public record DebugSubscriptionPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<DebugSubscriptionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Climora.MOD_ID, "debug_subscription"));

    public static final StreamCodec<FriendlyByteBuf, DebugSubscriptionPayload> CODEC =
            ByteBufCodecs.BOOL.map(DebugSubscriptionPayload::new, DebugSubscriptionPayload::enabled).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
