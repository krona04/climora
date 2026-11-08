package net.krona.climora.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.krona.climora.climate.AtmosphereModel;
import net.krona.climora.config.ClimoraConfig;
import net.krona.climora.weather.LocalWeather;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

/**
 * Weather narrows the view: radiation fog on still, humid nights, and poor visibility in a downpour or
 * heavy snow. Applied on top of the fog vanilla (and NeoForge events) already set up, and only ever
 * closer: effects like blindness keep winning.
 */
public final class WeatherFog {
    /** How far you see in full radiation fog, blocks. */
    private static final float FOG_VISIBILITY = 20.0F;
    /** How far you see in the heaviest downpour and the heaviest snowfall, blocks. */
    private static final float DOWNPOUR_VISIBILITY = 56.0F;
    private static final float SNOWFALL_VISIBILITY = 24.0F;
    /** Precipitation starts to hide the distance above this intensity. */
    private static final float HEAVY_START = 0.55F;
    /** Radiation fog lies on the ground: it thins out between these heights above the terrain, blocks. */
    private static final float FOG_TOP_START = 12.0F;
    private static final float FOG_TOP_END = 40.0F;

    private WeatherFog() {
    }

    /** Narrows the fog set up for this frame. Called at the end of {@link FogRenderer#setupFog}. */
    public static void apply(Camera camera, FogRenderer.FogMode mode) {
        if (camera.getFluidInCamera() != FogType.NONE) {
            return;
        }
        Density density = density(camera);
        if (density == null) {
            return;
        }
        float start = RenderSystem.getShaderFogStart();
        float end = RenderSystem.getShaderFogEnd();
        float target = density.visibility;
        if (mode == FogRenderer.FogMode.FOG_SKY) {
            // The sky washes out too, just a little behind the ground.
            target *= 1.5F;
        }
        float newEnd = Math.min(end, Mth.lerp(density.strength, end, target));
        float newStart = Math.min(start, Mth.lerp(density.strength, start, newEnd * 0.05F));
        RenderSystem.setShaderFogStart(Math.min(newStart, newEnd));
        RenderSystem.setShaderFogEnd(newEnd);
        if (density.strength > 0.3F && mode == FogRenderer.FogMode.FOG_TERRAIN) {
            // A dense fog closes in from every side, not only at the edge of the view.
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
        }
    }

    /**
     * Radiation fog is grey-white whatever the sky colour is, so the fog colour loses its saturation.
     * Returns the colour to use, or the same array when there is no fog.
     */
    public static float[] tint(Camera camera, float red, float green, float blue) {
        Density density = density(camera);
        if (density == null || density.radiationFog <= 0.0F || camera.getFluidInCamera() != FogType.NONE) {
            return new float[]{red, green, blue};
        }
        float grey = 0.3F * red + 0.59F * green + 0.11F * blue;
        float blend = density.radiationFog * 0.75F;
        return new float[]{Mth.lerp(blend, red, grey), Mth.lerp(blend, green, grey), Mth.lerp(blend, blue, grey * 1.02F)};
    }

    private record Density(float strength, float visibility, float radiationFog) {
    }

    private static Density density(Camera camera) {
        ClientWeather weather = ClientWeather.INSTANCE;
        if (!weather.isActive() || !ClimoraConfig.client().weatherFog) {
            return null;
        }
        Vec3 position = camera.getPosition();
        float fog = weather.fog(position.x, position.z);
        float elevation = weather.elevation(position.x, position.z);
        if (!Float.isNaN(elevation)) {
            fog *= 1.0F - AtmosphereModel.smoothstep(elevation + FOG_TOP_START, elevation + FOG_TOP_END, (float) position.y);
        }
        float heavy = AtmosphereModel.smoothstep(HEAVY_START, 1.0F, weather.precipitation(position.x, position.z));
        if (fog <= 0.0F && heavy <= 0.0F) {
            return null;
        }
        boolean snow = LocalWeather.precipitationType(weather, position.x, position.y, position.z)
                == AtmosphereModel.PrecipitationType.SNOW;
        float precipitationVisibility = snow ? SNOWFALL_VISIBILITY : DOWNPOUR_VISIBILITY;
        // Whichever hides more wins; they do not add up.
        return fog >= heavy
                ? new Density(fog, FOG_VISIBILITY, fog)
                : new Density(heavy, precipitationVisibility, fog);
    }
}
