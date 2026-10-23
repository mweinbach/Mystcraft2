/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.render;

/** Column-major 4x4 matrix helpers (index = column * 4 + row), matching Metal and OpenGL. */
public final class FrameMath {

    private FrameMath() {
    }

    public static float[] multiply(final float[] a, final float[] b) {
        final float[] r = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0.0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * b[col * 4 + k];
                }
                r[col * 4 + row] = sum;
            }
        }
        return r;
    }

    /** General inverse via cofactors (computed in double); returns identity for singular input. */
    public static float[] invert(final float[] m) {
        final double[] a = new double[16];
        for (int i = 0; i < 16; i++) {
            a[i] = m[i];
        }
        final double[] inv = new double[16];
        inv[0] = a[5] * a[10] * a[15] - a[5] * a[11] * a[14] - a[9] * a[6] * a[15] + a[9] * a[7] * a[14] + a[13] * a[6] * a[11] - a[13] * a[7] * a[10];
        inv[4] = -a[4] * a[10] * a[15] + a[4] * a[11] * a[14] + a[8] * a[6] * a[15] - a[8] * a[7] * a[14] - a[12] * a[6] * a[11] + a[12] * a[7] * a[10];
        inv[8] = a[4] * a[9] * a[15] - a[4] * a[11] * a[13] - a[8] * a[5] * a[15] + a[8] * a[7] * a[13] + a[12] * a[5] * a[11] - a[12] * a[7] * a[9];
        inv[12] = -a[4] * a[9] * a[14] + a[4] * a[10] * a[13] + a[8] * a[5] * a[14] - a[8] * a[6] * a[13] - a[12] * a[5] * a[10] + a[12] * a[6] * a[9];
        inv[1] = -a[1] * a[10] * a[15] + a[1] * a[11] * a[14] + a[9] * a[2] * a[15] - a[9] * a[3] * a[14] - a[13] * a[2] * a[11] + a[13] * a[3] * a[10];
        inv[5] = a[0] * a[10] * a[15] - a[0] * a[11] * a[14] - a[8] * a[2] * a[15] + a[8] * a[3] * a[14] + a[12] * a[2] * a[11] - a[12] * a[3] * a[10];
        inv[9] = -a[0] * a[9] * a[15] + a[0] * a[11] * a[13] + a[8] * a[1] * a[15] - a[8] * a[3] * a[13] - a[12] * a[1] * a[11] + a[12] * a[3] * a[9];
        inv[13] = a[0] * a[9] * a[14] - a[0] * a[10] * a[13] - a[8] * a[1] * a[14] + a[8] * a[2] * a[13] + a[12] * a[1] * a[10] - a[12] * a[2] * a[9];
        inv[2] = a[1] * a[6] * a[15] - a[1] * a[7] * a[14] - a[5] * a[2] * a[15] + a[5] * a[3] * a[14] + a[13] * a[2] * a[7] - a[13] * a[3] * a[6];
        inv[6] = -a[0] * a[6] * a[15] + a[0] * a[7] * a[14] + a[4] * a[2] * a[15] - a[4] * a[3] * a[14] - a[12] * a[2] * a[7] + a[12] * a[3] * a[6];
        inv[10] = a[0] * a[5] * a[15] - a[0] * a[7] * a[13] - a[4] * a[1] * a[15] + a[4] * a[3] * a[13] + a[12] * a[1] * a[7] - a[12] * a[3] * a[5];
        inv[14] = -a[0] * a[5] * a[14] + a[0] * a[6] * a[13] + a[4] * a[1] * a[14] - a[4] * a[2] * a[13] - a[12] * a[1] * a[6] + a[12] * a[2] * a[5];
        inv[3] = -a[1] * a[6] * a[11] + a[1] * a[7] * a[10] + a[5] * a[2] * a[11] - a[5] * a[3] * a[10] - a[9] * a[2] * a[7] + a[9] * a[3] * a[6];
        inv[7] = a[0] * a[6] * a[11] - a[0] * a[7] * a[10] - a[4] * a[2] * a[11] + a[4] * a[3] * a[10] + a[8] * a[2] * a[7] - a[8] * a[3] * a[6];
        inv[11] = -a[0] * a[5] * a[11] + a[0] * a[7] * a[9] + a[4] * a[1] * a[11] - a[4] * a[3] * a[9] - a[8] * a[1] * a[7] + a[8] * a[3] * a[5];
        inv[15] = a[0] * a[5] * a[10] - a[0] * a[6] * a[9] - a[4] * a[1] * a[10] + a[4] * a[2] * a[9] + a[8] * a[1] * a[6] - a[8] * a[2] * a[5];
        final double det = a[0] * inv[0] + a[1] * inv[4] + a[2] * inv[8] + a[3] * inv[12];
        final float[] r = identity();
        if (Math.abs(det) < 1e-30) {
            return r;
        }
        for (int i = 0; i < 16; i++) {
            r[i] = (float) (inv[i] / det);
        }
        return r;
    }

    public static float[] identity() {
        final float[] r = new float[16];
        r[0] = r[5] = r[10] = r[15] = 1.0f;
        return r;
    }

    /**
     * Six normalized planes (nx, ny, nz, d) in the matrix's input space; a point p is inside when
     * dot(n, p) + d >= 0 for every plane. Order: left, right, bottom, top, near, far.
     */
    public static float[] frustumPlanes(final float[] m) {
        final float[] planes = new float[24];
        final int[][] combos = {{0, 1}, {0, -1}, {1, 1}, {1, -1}, {2, 1}, {2, -1}};
        for (int p = 0; p < 6; p++) {
            final int row = combos[p][0];
            final float sign = combos[p][1];
            float nx = m[3] + sign * m[row];
            float ny = m[7] + sign * m[4 + row];
            float nz = m[11] + sign * m[8 + row];
            float d = m[15] + sign * m[12 + row];
            final float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-12f) {
                nx /= len;
                ny /= len;
                nz /= len;
                d /= len;
            }
            planes[p * 4] = nx;
            planes[p * 4 + 1] = ny;
            planes[p * 4 + 2] = nz;
            planes[p * 4 + 3] = d;
        }
        return planes;
    }

    /** Radical inverse of {@code index} (>= 1) in {@code base}: the Halton sequence in [0, 1). */
    public static float halton(int index, final int base) {
        float f = 1.0f;
        float r = 0.0f;
        while (index > 0) {
            f /= base;
            r += f * (index % base);
            index /= base;
        }
        return r;
    }

    /**
     * Applies a sub-pixel jitter J * viewProj that moves image content {@code jx} pixels right and
     * {@code jy} pixels down at a {@code width x height} render resolution.
     */
    public static float[] jitter(final float[] viewProj, final float jx, final float jy, final int width, final int height) {
        final float sx = 2.0f * jx / width;
        final float sy = -2.0f * jy / height;
        final float[] r = viewProj.clone();
        for (int col = 0; col < 4; col++) {
            final float w = viewProj[col * 4 + 3];
            r[col * 4] += sx * w;
            r[col * 4 + 1] += sy * w;
        }
        return r;
    }
}
