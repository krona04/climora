package net.krona.climora.network;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.krona.climora.climate.CellPos;
import net.krona.climora.climate.ClimateCell;
import net.krona.climora.climate.ClimateInterpolation;
import net.krona.climora.climate.ClimateManager;
import net.krona.climora.climate.ClimateSimulation;
import net.krona.climora.climate.WeatherOverride;
import net.krona.climora.weather.LocalWeather;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Sends climate debug data to players who have the F3 screen open.
 * Nothing is sent to players who did not subscribe, so the overlay costs nothing when unused.
 */
public final class ClimateDebugSync {
    private static final int SEND_INTERVAL_TICKS = 10;
    private static final Set<UUID> SUBSCRIBERS = new HashSet<>();

    private ClimateDebugSync() {
    }

    public static void init() {
        if (Platform.getEnvironment() == Env.SERVER) {
            // On a dedicated server the S2C type only has to be known; the client registers the receiver.
            NetworkManager.registerS2CPayloadType(ClimateDebugPayload.TYPE, ClimateDebugPayload.CODEC);
        }
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, DebugSubscriptionPayload.TYPE,
                DebugSubscriptionPayload.CODEC, (payload, context) -> context.queue(() -> {
                    UUID player = context.getPlayer().getUUID();
                    if (payload.enabled()) {
                        SUBSCRIBERS.add(player);
                    } else {
                        SUBSCRIBERS.remove(player);
                    }
                }));

        TickEvent.SERVER_POST.register(ClimateDebugSync::onServerTick);
        PlayerEvent.PLAYER_QUIT.register(player -> SUBSCRIBERS.remove(player.getUUID()));
        LifecycleEvent.SERVER_STOPPED.register(server -> SUBSCRIBERS.clear());
    }

    private static void onServerTick(MinecraftServer server) {
        if (SUBSCRIBERS.isEmpty() || server.getTickCount() % SEND_INTERVAL_TICKS != 0) {
            return;
        }
        Iterator<UUID> iterator = SUBSCRIBERS.iterator();
        while (iterator.hasNext()) {
            ServerPlayer player = server.getPlayerList().getPlayer(iterator.next());
            if (player == null) {
                iterator.remove();
                continue;
            }
            // Respect the vanilla switch that hides debug information from players.
            if (player.serverLevel().getGameRules().getBoolean(GameRules.RULE_REDUCEDDEBUGINFO)) {
                continue;
            }
            NetworkManager.sendToPlayer(player, createPayload(player));
        }
    }

    private static ClimateDebugPayload createPayload(ServerPlayer player) {
        ClimateSimulation simulation = ClimateManager.get(player.serverLevel());
        if (simulation == null) {
            return ClimateDebugPayload.notSimulated();
        }

        int cellX = CellPos.blockToCell(player.getBlockX());
        int cellZ = CellPos.blockToCell(player.getBlockZ());
        ClimateCell cell = simulation.grid().get(CellPos.key(cellX, cellZ));
        ClimateInterpolation.Sample sample = simulation.sample(player.getX(), player.getZ());

        WeatherOverride override = simulation.overrideAt(cellX, cellZ);
        boolean hasSample = sample != null;
        boolean hasCell = cell != null;

        return new ClimateDebugPayload(
                true,
                cellX,
                cellZ,
                hasCell,
                hasSample ? sample.airTemperature(player.getY()) : Float.NaN,
                hasSample ? sample.surfaceElevation() : Float.NaN,
                hasSample ? sample.pressure() : 0.0F,
                hasSample ? sample.relativeHumidity() : 0.0F,
                hasSample ? sample.cloudCover() : 0.0F,
                hasSample ? sample.precipitation() : 0.0F,
                hasSample ? LocalWeather.precipitationAt(simulation, player.blockPosition()).ordinal() : 0,
                hasSample ? sample.storm() : 0.0F,
                hasSample ? sample.windX() : 0.0F,
                hasSample ? sample.windZ() : 0.0F,
                hasCell ? cell.temperature() : 0.0F,
                hasCell ? simulation.targetTemperature(cell) : 0.0F,
                hasCell ? cell.baseTemperature() : 0.0F,
                override != null ? override.mode().ordinal() + 1 : 0,
                simulation.activeCellCount(),
                (float) simulation.stats().averageTickMillis());
    }
}
