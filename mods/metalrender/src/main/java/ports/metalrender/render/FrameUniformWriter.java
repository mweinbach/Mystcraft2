/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.render;

import java.nio.ByteBuffer;

/**
 * Writes the {@code FrameUniforms} (272 bytes) and {@code TemporalUniforms} (160 bytes) blocks from
 * {@code metal_shared.h}. Buffers must be little-endian; writes are absolute from index 0.
 */
public final class FrameUniformWriter {

    public static final int FRAME_BYTES = 272;
    public static final int TEMPORAL_BYTES = 160;

    private FrameUniformWriter() {
    }

    /**
     * @param viewProj    GL projection * view rotation with the camera at the origin (jittered when
     *                    MetalFX temporal is active)
     * @param planes      frustum planes of the unjittered matrix ({@link FrameMath#frustumPlanes})
     * @param renderDistanceBlocks horizontal draw distance used by the GPU culling
     * @param fogShape    0 sphere, 1 cylinder
     */
    public static void writeFrame(final ByteBuffer out, final float[] viewProj, final float[] planes,
                                  final double camX, final double camY, final double camZ,
                                  final float renderDistanceBlocks, final float[] fogColor,
                                  final float fogStart, final float fogEnd, final int fogShape) {
        putFloats(out, 0, viewProj, 16);
        putFloats(out, 64, planes, 24);
        final int bx = (int) Math.floor(camX);
        final int by = (int) Math.floor(camY);
        final int bz = (int) Math.floor(camZ);
        out.putInt(160, bx);
        out.putInt(164, by);
        out.putInt(168, bz);
        out.putInt(172, 0);
        out.putFloat(176, (float) (camX - bx));
        out.putFloat(180, (float) (camY - by));
        out.putFloat(184, (float) (camZ - bz));
        out.putFloat(188, renderDistanceBlocks);
        putFloats(out, 192, fogColor, 4);
        out.putFloat(208, fogStart);
        out.putFloat(212, fogEnd);
        out.putFloat(216, fogShape);
        out.putFloat(220, 0.0f);
        for (int i = 224; i < 240; i += 4) {
            out.putInt(i, 0); // counts: filled in natively
        }
        out.putInt(240, 0); // LOD disabled
        out.putInt(244, 0);
        out.putInt(248, 0);
        out.putInt(252, 0); // Hi-Z occlusion disabled
        out.putFloat(256, 0.0f); // screenParams: filled in natively
        out.putFloat(260, 0.0f);
        out.putFloat(264, 0.0f);
        out.putFloat(268, 1.0f);
    }

    public static void writeTemporal(final ByteBuffer out, final TemporalState.Frame frame) {
        putFloats(out, 0, frame.invViewProj(), 16);
        putFloats(out, 64, frame.prevViewProj(), 16);
        out.putFloat(128, frame.deltaX());
        out.putFloat(132, frame.deltaY());
        out.putFloat(136, frame.deltaZ());
        out.putFloat(140, 0.0f);
        out.putFloat(144, frame.jitterX());
        out.putFloat(148, frame.jitterY());
        out.putFloat(152, frame.reset() ? 1.0f : 0.0f);
        out.putFloat(156, 0.0f);
    }

    private static void putFloats(final ByteBuffer out, final int offset, final float[] values, final int count) {
        for (int i = 0; i < count; i++) {
            out.putFloat(offset + i * 4, values[i]);
        }
    }
}
