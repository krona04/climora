package net.krona.climora.client;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.level.entity.EntityModelLayerRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.client.rendering.ColorHandlerRegistry;
import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import net.krona.climora.client.dev.DevHarness;
import net.krona.climora.config.ClimoraConfig;
import net.krona.climora.network.ClimateDebugPayload;
import net.krona.climora.network.LocalWeatherPayload;
import net.krona.climora.registry.ClimoraRegistries;
import net.krona.climora.registry.ClimoraRegistries;
import net.krona.climora.update.UpdateChecker;
import net.krona.climora.update.UpdateNotifier;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.RenderType;

/**
 * Client-only setup. Called through {@code EnvExecutor}, never load this class on a dedicated server.
 */
public final class ClimoraClient {
    private ClimoraClient() {
    }

    public static void init() {
        ClimoraConfig.loadClient();
        UpdateChecker.start();
        UpdateNotifier.init();
        net.krona.climora.update.SupportNotifier.init();

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
        // See-through ice and water, tinted like the water of the biome.
        ClimoraRegistries.GLAZE.listen(block -> RenderTypeRegistry.register(RenderType.translucent(), block));
        ClimoraRegistries.PUDDLE.listen(block -> RenderTypeRegistry.register(RenderType.translucent(), block));
        ColorHandlerRegistry.registerBlockColors((state, level, pos, tintIndex) ->
                level != null && pos != null ? BiomeColors.getAverageWaterColor(level, pos) : 0x3F76E4, ClimoraRegistries.PUDDLE);
        ClimoraRegistries.WEATHER_VANE_ENTITY.listen(type -> BlockEntityRendererRegistry.register(type, WeatherVaneRenderer::new));

        DevHarness.init();
        ClientLifecycleEvent.CLIENT_STOPPING.register(minecraft -> CloudRenderer.close());
    }
}
