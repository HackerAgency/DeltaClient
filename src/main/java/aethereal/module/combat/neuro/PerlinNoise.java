package aethereal.module.combat.neuro;

import java.util.Random;

public final class PerlinNoise {
    private final int[] p = new int[512];

    public PerlinNoise(int seed) {
        int[] perm = new int[256];
        for (int i = 0; i < 256; i++) {
            perm[i] = i;
        }

        Random r = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = perm[i];
            perm[i] = perm[j];
            perm[j] = t;
        }

        for (int i = 0; i < 256; i++) {
            int v = perm[i] & 255;
            p[i] = v;
            p[i + 256] = v;
        }
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    private static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }

    private static float grad(int hash, float x, float y, float z) {
        int h = hash & 15;
        float u = h < 8 ? x : y;
        float v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    public float noise(float x, float y) {
        float z = 0.0f;

        int X = ((int) Math.floor(x)) & 255;
        int Y = ((int) Math.floor(y)) & 255;
        int Z = ((int) Math.floor(z)) & 255;

        x = (float) (x - Math.floor(x));
        y = (float) (y - Math.floor(y));
        z = (float) (z - Math.floor(z));

        float u = fade(x);
        float v = fade(y);
        float w = fade(z);

        int A = p[X] + Y;
        int AA = p[A] + Z;
        int AB = p[A + 1] + Z;
        int B = p[X + 1] + Y;
        int BA = p[B] + Z;
        int BB = p[B + 1] + Z;

        return lerp(w,
                lerp(v,
                        lerp(u, grad(p[AA], x, y, z), grad(p[BA], x - 1.0f, y, z)),
                        lerp(u, grad(p[AB], x, y - 1.0f, z), grad(p[BB], x - 1.0f, y - 1.0f, z))
                ),
                lerp(v,
                        lerp(u, grad(p[AA + 1], x, y, z - 1.0f), grad(p[BA + 1], x - 1.0f, y, z - 1.0f)),
                        lerp(u, grad(p[AB + 1], x, y - 1.0f, z - 1.0f), grad(p[BB + 1], x - 1.0f, y - 1.0f, z - 1.0f))
                )
        );
    }

    public float fbm(float x, float y, int octaves, float lacunarity, float gain) {
        float sum = 0.0f;
        float amp = 0.5f;
        float fx = x;
        float fy = y;
        for (int i = 0; i < octaves; i++) {
            sum += noise(fx, fy) * amp;
            fx *= lacunarity;
            fy *= lacunarity;
            amp *= gain;
        }
        return sum;
    }
}
