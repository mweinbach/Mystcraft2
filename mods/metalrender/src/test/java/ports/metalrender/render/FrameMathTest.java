/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class FrameMathTest {

    /** OpenGL perspective (column-major), like Matrix4f.perspective. */
    static float[] perspective(final float fovYDeg, final float aspect, final float near, final float far) {
        final float f = (float) (1.0 / Math.tan(Math.toRadians(fovYDeg) / 2.0));
        final float[] m = new float[16];
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1.0f;
        m[14] = 2.0f * far * near / (near - far);
        return m;
    }

    static float[] transform(final float[] m, final float x, final float y, final float z) {
        final float[] r = new float[4];
        for (int row = 0; row < 4; row++) {
            r[row] = m[row] * x + m[4 + row] * y + m[8 + row] * z + m[12 + row];
        }
        return r;
    }

    private static boolean inside(final float[] planes, final float x, final float y, final float z) {
        for (int p = 0; p < 6; p++) {
            if (planes[p * 4] * x + planes[p * 4 + 1] * y + planes[p * 4 + 2] * z + planes[p * 4 + 3] < 0) {
                return false;
            }
        }
        return true;
    }

    @Test
    void multipliesAndInvertsColumnMajorMatrices() {
        final float[] proj = perspective(70, 16f / 9f, 0.05f, 512f);
        final float[] view = FrameMath.identity();
        view[12] = 3; // translation
        view[13] = -2;
        final float[] vp = FrameMath.multiply(proj, view);
        final float[] round = FrameMath.multiply(FrameMath.invert(vp), vp);
        for (int i = 0; i < 16; i++) {
            assertEquals(i % 5 == 0 ? 1.0f : 0.0f, round[i], 1e-4f, "element " + i);
        }
        assertArrayEquals(proj, FrameMath.multiply(proj, FrameMath.identity()));
    }

    @Test
    void extractsInwardFacingFrustumPlanes() {
        final float[] planes = FrameMath.frustumPlanes(perspective(70, 1.0f, 0.05f, 100f));
        assertTrue(inside(planes, 0, 0, -10), "straight ahead");
        assertFalse(inside(planes, 0, 0, 10), "behind the camera");
        assertFalse(inside(planes, 50, 0, -10), "far right");
        assertFalse(inside(planes, 0, 0, -200), "beyond the far plane");
        for (int p = 0; p < 6; p++) {
            final float len = (float) Math.sqrt(planes[p * 4] * planes[p * 4] + planes[p * 4 + 1] * planes[p * 4 + 1]
                    + planes[p * 4 + 2] * planes[p * 4 + 2]);
            assertEquals(1.0f, len, 1e-5f);
        }
    }

    @Test
    void haltonSequenceMatchesKnownValues() {
        assertEquals(0.5f, FrameMath.halton(1, 2));
        assertEquals(0.25f, FrameMath.halton(2, 2));
        assertEquals(0.75f, FrameMath.halton(3, 2));
        assertEquals(1.0f / 3.0f, FrameMath.halton(1, 3), 1e-6f);
        assertEquals(2.0f / 3.0f, FrameMath.halton(2, 3), 1e-6f);
        assertEquals(1.0f / 9.0f, FrameMath.halton(3, 3), 1e-6f);
    }

    @Test
    void jitterShiftsContentRightAndDownInPixels() {
        final float[] vp = perspective(70, 1.0f, 0.05f, 100f);
        final float[] jittered = FrameMath.jitter(vp, 0.5f, 0.25f, 200, 100);
        final float[] a = transform(vp, 1, 2, -10);
        final float[] b = transform(jittered, 1, 2, -10);
        final float pxA = (a[0] / a[3] * 0.5f + 0.5f) * 200;
        final float pxB = (b[0] / b[3] * 0.5f + 0.5f) * 200;
        final float pyA = (0.5f - a[1] / a[3] * 0.5f) * 100;
        final float pyB = (0.5f - b[1] / b[3] * 0.5f) * 100;
        assertEquals(0.5f, pxB - pxA, 1e-3f);
        assertEquals(0.25f, pyB - pyA, 1e-3f);
    }

    @Test
    void temporalStateResetsAndCyclesJitter() {
        final TemporalState state = new TemporalState();
        final float[] vp = perspective(70, 1.0f, 0.05f, 100f);
        final TemporalState.Frame first = state.next(vp, 0, 64, 0, 100, 100);
        assertTrue(first.reset());
        final TemporalState.Frame second = state.next(vp, 0.5, 64, 0, 100, 100);
        assertFalse(second.reset());
        assertEquals(0.5f, second.deltaX(), 1e-6f);
        assertTrue(Math.abs(second.jitterX()) <= 0.5f && Math.abs(second.jitterY()) <= 0.5f);
        assertTrue(state.next(vp, 1000, 64, 0, 100, 100).reset(), "teleport");
        assertTrue(state.next(vp, 1000, 64, 0, 50, 100).reset(), "resize");
        state.invalidate();
        assertTrue(state.next(vp, 1000, 64, 0, 50, 100).reset(), "invalidate");
    }

    @Test
    void writesUniformBlocksAtTheNativeOffsets() {
        final ByteBuffer frame = ByteBuffer.allocate(FrameUniformWriter.FRAME_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        final float[] vp = perspective(70, 1.0f, 0.05f, 100f);
        FrameUniformWriter.writeFrame(frame, vp, FrameMath.frustumPlanes(vp), -10.25, 70.5, 3.75, 192f,
                new float[] {0.1f, 0.2f, 0.3f, 1.0f}, 16f, 180f, 1);
        assertEquals(vp[11], frame.getFloat(44));
        assertEquals(-11, frame.getInt(160));
        assertEquals(70, frame.getInt(164));
        assertEquals(3, frame.getInt(168));
        assertEquals(0.75f, frame.getFloat(176));
        assertEquals(0.5f, frame.getFloat(180));
        assertEquals(0.75f, frame.getFloat(184));
        assertEquals(192f, frame.getFloat(188));
        assertEquals(0.3f, frame.getFloat(200));
        assertEquals(16f, frame.getFloat(208));
        assertEquals(180f, frame.getFloat(212));
        assertEquals(1f, frame.getFloat(216));
        assertEquals(0, frame.getInt(252), "Hi-Z occlusion stays off");
        assertEquals(1f, frame.getFloat(268));

        final ByteBuffer temporal = ByteBuffer.allocate(FrameUniformWriter.TEMPORAL_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        FrameUniformWriter.writeTemporal(temporal, new TemporalState.Frame(FrameMath.identity(), vp,
                1f, 2f, 3f, 0.25f, -0.125f, true));
        assertEquals(1f, temporal.getFloat(0));
        assertEquals(vp[14], temporal.getFloat(64 + 56));
        assertEquals(2f, temporal.getFloat(132));
        assertEquals(0.25f, temporal.getFloat(144));
        assertEquals(-0.125f, temporal.getFloat(148));
        assertEquals(1f, temporal.getFloat(152));
    }
}
