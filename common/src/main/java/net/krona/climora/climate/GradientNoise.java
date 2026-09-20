package net.krona.climora.climate;

import java.util.Random;

/**
 * Seeded 3D gradient noise (improved Perlin). Returns values roughly in [-1, 1].
 * Pure Java, so weather fields are identical on every platform and testable without Minecraft.
 */
public final class GradientNoise {
    private static final int[][] GRADIENTS = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
            {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}
    };

    private final int[] permutation = new int[512];

    public GradientNoise(long seed) {
        int[] base = new int[256];
        for (int i = 0; i < 256; i++) {
            base[i] = i;
        }
        Random random = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = base[i];
            base[i] = base[j];
            base[j] = swap;
        }
        for (int i = 0; i < 512; i++) {
            permutation[i] = base[i & 255];
        }
    }

    public double sample(double x, double y, double z) {
        int xi = floor(x);
        int yi = floor(y);
        int zi = floor(z);
        double xf = x - xi;
        double yf = y - yi;
        double zf = z - zi;
        xi &= 255;
        yi &= 255;
        zi &= 255;

        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);

        int a = permutation[xi] + yi;
        int aa = permutation[a] + zi;
        int ab = permutation[a + 1] + zi;
        int b = permutation[xi + 1] + yi;
        int ba = permutation[b] + zi;
        int bb = permutation[b + 1] + zi;

        return lerp(w,
                lerp(v,
                        lerp(u, grad(permutation[aa], xf, yf, zf), grad(permutation[ba], xf - 1, yf, zf)),
                        lerp(u, grad(permutation[ab], xf, yf - 1, zf), grad(permutation[bb], xf - 1, yf - 1, zf))),
                lerp(v,
                        lerp(u, grad(permutation[aa + 1], xf, yf, zf - 1), grad(permutation[ba + 1], xf - 1, yf, zf - 1)),
                        lerp(u, grad(permutation[ab + 1], xf, yf - 1, zf - 1), grad(permutation[bb + 1], xf - 1, yf - 1, zf - 1))));
    }

    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y, double z) {
        int[] g = GRADIENTS[hash & 15];
        return g[0] * x + g[1] * y + g[2] * z;
    }
}
