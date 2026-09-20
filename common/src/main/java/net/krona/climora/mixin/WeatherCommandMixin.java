package net.krona.climora.mixin;

import net.krona.climora.climate.WeatherOverride;
import net.krona.climora.command.ClimoraCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.WeatherCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla {@code /weather} would only change the unused global weather. With local weather it
 * forces the requested weather in a large area around the player instead.
 */
@Mixin(WeatherCommand.class)
abstract class WeatherCommandMixin {
    @Inject(method = "setClear", at = @At("HEAD"), cancellable = true)
    private static void climora$clear(CommandSourceStack source, int duration, CallbackInfoReturnable<Integer> cir) {
        if (ClimoraCommand.redirectVanillaWeather(source, WeatherOverride.Mode.CLEAR, duration)) {
            cir.setReturnValue(duration);
        }
    }

    @Inject(method = "setRain", at = @At("HEAD"), cancellable = true)
    private static void climora$rain(CommandSourceStack source, int duration, CallbackInfoReturnable<Integer> cir) {
        if (ClimoraCommand.redirectVanillaWeather(source, WeatherOverride.Mode.RAIN, duration)) {
            cir.setReturnValue(duration);
        }
    }

    @Inject(method = "setThunder", at = @At("HEAD"), cancellable = true)
    private static void climora$thunder(CommandSourceStack source, int duration, CallbackInfoReturnable<Integer> cir) {
        if (ClimoraCommand.redirectVanillaWeather(source, WeatherOverride.Mode.STORM, duration)) {
            cir.setReturnValue(duration);
        }
    }
}
