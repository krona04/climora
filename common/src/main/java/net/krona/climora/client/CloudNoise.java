package net.krona.climora.client;

import net.krona.climora.climate.GradientNoise;

/**
 * Fixed noise pattern that decides which cloud tiles fill first as cloud cover grows.
 * The same on every client, so players see the same clouds.
 */
final class CloudNoise {
    private static final GradientNoise NOISE = new GradientNoise(0x7E57_C10D_5L);

    private CloudNoise() {
    }

    /**
     * Roughly uniform value in [0, 1] for a cloud tile. The lowest octave is the widest, so clouds come out
     * as whole banks rather than single scattered tiles; the finer octaves only ruffle their edges.
     */
    static float sample(int tileX, int tileZ, double evolution) {
        double value = NOISE.sample(tileX / 14.0, tileZ / 14.0, evolution)
                + 0.45 * NOISE.sample(tileX / 5.0, tileZ / 5.0, evolution * 1.7 + 17.0)
                + 0.18 * NOISE.sample(tileX / 2.0, tileZ / 2.0, evolution * 2.3 + 41.0);
        // Gradient noise clusters around 0; stretch it so cover 0.5 fills about half of the sky.
        double normalized = value / 1.05;
        return (float) Math.max(0.0, Math.min(1.0, 0.5 + normalized * 0.9));
    }

    /**
     * Value in [0, 1] used for the height of a cloud's top. Much wider than the cover pattern, so the top
     * rises and falls over whole banks instead of every tile standing at its own height.
     */
    static float shape(int tileX, int tileZ, double evolution) {
        double value = NOISE.sample(tileX / 20.0 + 128.0, tileZ / 20.0 - 128.0, evolution * 0.5)
                + 0.35 * NOISE.sample(tileX / 7.0 + 64.0, tileZ / 7.0 - 64.0, evolution * 0.8 + 23.0);
        return (float) Math.max(0.0, Math.min(1.0, 0.5 + value / 1.35 * 0.9));
    }
}
