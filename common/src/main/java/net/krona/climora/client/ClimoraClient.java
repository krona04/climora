package net.krona.climora.client;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.level.entity.EntityModelLayerRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import net.krona.climora.client.dev.DevHarness;
import net.krona.climora.config.ClimoraConfig;
import net.krona.climora.network.ClimateDebugPayload;
import net.krona.climora.network.LocalWeatherPayload;
import net.krona.climora.registry.ClimoraRegistries;

/**
 * Client-only setup. Called through {@code EnvExecutor}, never load this class on a dedicated server.
 */
public final class ClimoraClient {
    private ClimoraClient() {
    }

    public static void init() {
        ClimoraConfig.loadClient();

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ClimateDebugPayload.TYPE, ClimateDebugPayload.CODEC,
                (payload, context) -> context.queue(() -> ClimateDebugOverlay.accept(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, LocalWeatherPayload.TYPE, LocalWeatherPayload.CODEC,
                (payload, context) -> context.queue(() -> ClientWeather.INSTANCE.accept(payload)));

        ClientTickEvent.CLIENT_POST.register(minecraft -> {
            ClientWeather.INSTANCE.tick(minecraft);
            ClimateDebugOverlay.tick(minecraft);
        });
        ClientGuiEvent.DEBUG_TEXT_LEFT.register(ClimateDebugOverlay::appendLines);
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            ClimateDebugOverlay.reset();
            ClientWeather.INSTANCE.reset();
            CloudRenderer.close();
        });

        EntityModelLayerRegistry.register(WeatherVaneRenderer.LAYER, WeatherVaneRenderer::createLayer);
        ClimoraRegistries.WEATHER_VANE_ENTITY.listen(type -> BlockEntityRendererRegistry.register(type, WeatherVaneRenderer::new));

        DevHarness.init();
        ClientLifecycleEvent.CLIENT_STOPPING.register(minecraft -> CloudRenderer.close());
    }
}
