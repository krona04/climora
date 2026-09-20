package net.krona.climora.network;

import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.krona.climora.climate.CellPos;
import net.krona.climora.climate.ClimateCell;
import net.krona.climora.climate.ClimateSimulation;
import net.krona.climora.config.ClimoraConfig;
import net.krona.climora.weather.LocalWeather;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends the weather around each player once a second, so clients can draw local rain, snow and clouds.
 */
public final class LocalWeatherSync {
    private static final int SEND_INTERVAL_TICKS = 20;

    private LocalWeatherSync() {
    }

    public static void init() {
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(LocalWeatherPayload.TYPE, LocalWeatherPayload.CODEC);
        }
        TickEvent.SERVER_POST.register(LocalWeatherSync::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % SEND_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!NetworkManager.canPlayerReceive(player, LocalWeatherPayload.TYPE)) {
                continue;
            }
            NetworkManager.sendToPlayer(player, createPayload(player));
        }
    }

    static LocalWeatherPayload createPayload(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!(LocalWeather.view(level) instanceof ClimateSimulation simulation)) {
            return LocalWeatherPayload.inactive();
        }

        long gameTime = level.getGameTime();
        int radius = ClimoraConfig.server().activeRadiusCells;
        int size = radius * 2 + 1;
        int originX = CellPos.blockToCell(player.getBlockX()) - radius;
        int originZ = CellPos.blockToCell(player.getBlockZ()) - radius;

        LocalWeatherPayload.Cell[] cells = new LocalWeatherPayload.Cell[size * size];
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                ClimateCell cell = simulation.grid().get(CellPos.key(originX + dx, originZ + dz));
                cells[dz * size + dx] = cell == null || !cell.isFullySampled()
                        ? LocalWeatherPayload.Cell.MISSING
                        : new LocalWeatherPayload.Cell(true, cell.temperature(), cell.elevation(), cell.relativeHumidity(),
                        cell.cloudCover(), cell.precipitation(), cell.storm(), cell.windX(), cell.windZ());
            }
        }

        return new LocalWeatherPayload(gameTime, originX, originZ, size,
                simulation.synoptic().driftOffsetX(gameTime),
                simulation.synoptic().driftOffsetZ(gameTime),
                (float) simulation.synoptic().driftVelocityX(gameTime),
                (float) simulation.synoptic().driftVelocityZ(gameTime),
                cells);
    }
}
