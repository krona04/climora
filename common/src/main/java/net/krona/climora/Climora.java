package net.krona.climora;

import com.mojang.logging.LogUtils;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import net.krona.climora.client.ClimoraClient;
import net.krona.climora.climate.ClimateManager;
import net.krona.climora.command.ClimoraCommand;
import net.krona.climora.config.ClimoraConfig;
import net.krona.climora.network.ClimateDebugSync;
import net.krona.climora.network.LocalWeatherSync;
import net.krona.climora.registry.ClimoraRegistries;
import org.slf4j.Logger;

public final class Climora {
    public static final String MOD_ID = "climora";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static void init() {
        ClimoraConfig.loadServer();
        ClimoraRegistries.init();
        ClimateManager.init();
        ClimateDebugSync.init();
        LocalWeatherSync.init();
        CommandRegistrationEvent.EVENT.register(ClimoraCommand::register);
        EnvExecutor.runInEnv(Env.CLIENT, () -> ClimoraClient::init);
    }
}
