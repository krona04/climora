package net.krona.climora.neoforge;

import net.krona.climora.Climora;
import net.krona.climora.weather.LocalWeather;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

@Mod(Climora.MOD_ID)
public final class ClimoraNeoForge {
    public ClimoraNeoForge() {
        // Run our common setup.
        Climora.init();
        NeoForge.EVENT_BUS.addListener(ClimoraNeoForge::onCanPlayerSleep);
    }

    /**
     * Beds work under a local thunderstorm by day, as they do under a vanilla one. On Fabric the same is
     * done by {@code ServerPlayerMixin}; NeoForge moved that check behind this event.
     */
    private static void onCanPlayerSleep(CanPlayerSleepEvent event) {
        if (event.getProblem() == Player.BedSleepingProblem.NOT_POSSIBLE_NOW
                && LocalWeather.isThunderingOver(event.getLevel(), event.getPos())) {
            event.setProblem(null);
        }
    }
}
