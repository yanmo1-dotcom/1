package com.yourname.voxelgame.world;

/**
 * 内联 Simplex 噪声（Stefan Gustavson 算法的 Java 实现，无外部依赖）。
 * 2D / 3D 噪声输出 [-1,1] 范围，可直接叠加为分形布朗运动 (fBm)。
 * 实例持有固定种子置换表，线程安全只读使用。
 */
public final class SimplexNoise {

    private static final int[][] grad3 = {
        {1,1,0},{-1,1,0},{1,-1,0},{-1,-1,0},
        {1,0,1},{-1,0,1},{1,0,-1},{-1,0,-1},
        {0,1,1},{0,-1,1},{0,1,-1},{0,-1,-1}
    };

    private final int[] perm = new int[512];
    private final int[] permMod12 = new int[512];

    public SimplexNoise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        // 线性同余洗牌（无 Math.random）
        long s = seed;
        for (int i = 255; i > 0; i--) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            int j = (int) ((s >>> 33) % (i + 1));
            int t = p[i]; p[i] = p[j]; p[j] = t;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
            permMod12[i] = perm[i] % 12;
        }
    }

    private static final float F2 = 0.5f * ((float) Math.sqrt(3) - 1f);
    private static final float G2 = (3f - (float) Math.sqrt(3)) / 6f;
    private static final float F3 = 1f / 3f;
    private static final float G3 = 1f / 6f;

    /** 2D Simplex 噪声，[-1,1]。 */
    public float noise2(float xin, float yin) {
        float s = (xin + yin) * F2;
        int i = fastfloor(xin + s);
        int j = fastfloor(yin + s);
        float t = (i + j) * G2;
        float X0 = i - t, Y0 = j - t;
        float x0 = xin - X0, y0 = yin - Y0;
        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; } else { i1 = 0; j1 = 1; }
        float x1 = x0 - i1 + G2, y1 = y0 - j1 + G2;
        float x2 = x0 - 1f + 2f * G2, y2 = y0 - 1f + 2f * G2;
        int ii = i & 255, jj = j & 255;
        int gi0 = permMod12[ii + perm[jj]];
        int gi1 = permMod12[ii + i1 + perm[jj + j1]];
        int gi2 = permMod12[ii + 1 + perm[jj + 1]];
        float n0 = 0, n1 = 0, n2 = 0;
        float t0 = 0.5f - x0 * x0 - y0 * y0;
        if (t0 >= 0) { t0 *= t0; n0 = t0 * t0 * (grad3[gi0][0] * x0 + grad3[gi0][1] * y0); }
        float t1 = 0.5f - x1 * x1 - y1 * y1;
        if (t1 >= 0) { t1 *= t1; n1 = t1 * t1 * (grad3[gi1][0] * x1 + grad3[gi1][1] * y1); }
        float t2 = 0.5f - x2 * x2 - y2 * y2;
        if (t2 >= 0) { t2 *= t2; n2 = t2 * t2 * (grad3[gi2][0] * x2 + grad3[gi2][1] * y2); }
        return 70f * (n0 + n1 + n2);
    }

    /** 3D Simplex 噪声，[-1,1]。 */
    public float noise3(float xin, float yin, float zin) {
        float s = (xin + yin + zin) * F3;
        int i = fastfloor(xin + s);
        int j = fastfloor(yin + s);
        int k = fastfloor(zin + s);
        float t = (i + j + k) * G3;
        float X0 = i - t, Y0 = j - t, Z0 = k - t;
        float x0 = xin - X0, y0 = yin - Y0, z0 = zin - Z0;
        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0)      { i1=1; j1=0; k1=0; i2=1; j2=1; k2=0; }
            else if (x0 >= z0){ i1=1; j1=0; k1=0; i2=1; j2=0; k2=1; }
            else              { i1=0; j1=0; k1=1; i2=1; j2=0; k2=1; }
        } else {
            if (y0 < z0)      { i1=0; j1=0; k1=1; i2=0; j2=1; k2=1; }
            else if (x0 < z0) { i1=0; j1=1; k1=0; i2=0; j2=1; k2=1; }
            else             { i1=0; j1=1; k1=0; i2=1; j2=1; k2=0; }
        }
        float x1 = x0 - i1 + G3, y1 = y0 - j1 + G3, z1 = z0 - k1 + G3;
        float x2 = x0 - i2 + 2f * G3, y2 = y0 - j2 + 2f * G3, z2 = z0 - k2 + 2f * G3;
        float x3 = x0 - 1f + 3f * G3, y3 = y0 - 1f + 3f * G3, z3 = z0 - 1f + 3f * G3;
        int ii = i & 255, jj = j & 255, kk = k & 255;
        int gi0 = permMod12[ii + perm[jj + perm[kk]]];
        int gi1 = permMod12[ii + i1 + perm[jj + j1 + perm[kk + k1]]];
        int gi2 = permMod12[ii + i2 + perm[jj + j2 + perm[kk + k2]]];
        int gi3 = permMod12[ii + 1 + perm[jj + 1 + perm[kk + 1]]];
        float n0 = 0, n1 = 0, n2 = 0, n3 = 0;
        float t0 = 0.6f - x0 * x0 - y0 * y0 - z0 * z0;
        if (t0 >= 0) { t0 *= t0; n0 = t0 * t0 * dot(grad3[gi0], x0, y0, z0); }
        float t1 = 0.6f - x1 * x1 - y1 * y1 - z1 * z1;
        if (t1 >= 0) { t1 *= t1; n1 = t1 * t1 * dot(grad3[gi1], x1, y1, z1); }
        float t2 = 0.6f - x2 * x2 - y2 * y2 - z2 * z2;
        if (t2 >= 0) { t2 *= t2; n2 = t2 * t2 * dot(grad3[gi2], x2, y2, z2); }
        float t3 = 0.6f - x3 * x3 - y3 * y3 - z3 * z3;
        if (t3 >= 0) { t3 *= t3; n3 = t3 * t3 * dot(grad3[gi3], x3, y3, z3); }
        return 32f * (n0 + n1 + n2 + n3);
    }

    private static float dot(int[] g, float x, float y, float z) {
        return g[0] * x + g[1] * y + g[2] * z;
    }

    private static int fastfloor(float x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    /** 2D 分形布朗运动：多倍频叠加。 */
    public float fbm2(float x, float y, int octaves, float persistence, float scale) {
        float total = 0f, amp = 1f, freq = scale, maxAmp = 0f;
        for (int i = 0; i < octaves; i++) {
            total += noise2(x * freq, y * freq) * amp;
            maxAmp += amp;
            amp *= persistence;
            freq *= 2f;
        }
        return total / maxAmp;
    }

    /** 3D 分形布朗运动。 */
    public float fbm3(float x, float y, float z, int octaves, float persistence, float scale) {
        float total = 0f, amp = 1f, freq = scale, maxAmp = 0f;
        for (int i = 0; i < octaves; i++) {
            total += noise3(x * freq, y * freq, z * freq) * amp;
            maxAmp += amp;
            amp *= persistence;
            freq *= 2f;
        }
        return total / maxAmp;
    }
}
