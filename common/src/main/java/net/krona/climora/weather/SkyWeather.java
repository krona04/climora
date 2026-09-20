package net.krona.climora.weather;

/**
 * Rain and thunder levels around the camera, used by vanilla sky, fog and sound code on the client.
 * Lives in common code so the {@code Level} mixin can use it without loading client classes.
 */
public interface SkyWeather {
    float rainLevel(float partialTick);

    float thunderLevel(float partialTick);
}
