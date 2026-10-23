/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.render;

/**
 * Per-frame jitter and camera history for MetalFX temporal upscaling. Jitter follows an 8-frame
 * Halton(2, 3) cycle centred on the pixel; history resets after resizes, teleports or a gap in
 * temporal frames.
 */
public final class TemporalState {

    private static final int CYCLE = 8;
    private static final double TELEPORT_DISTANCE_SQ = 32.0 * 32.0;

    private float[] prevViewProj;
    private double prevX;
    private double prevY;
    private double prevZ;
    private int prevWidth;
    private int prevHeight;
    private int frameIndex;

    /**
     * @param invViewProj  inverse of the unjittered current matrix
     * @param prevViewProj unjittered previous matrix (previous camera at origin)
     * @param jitterX      pixels right
     * @param jitterY      pixels down
     */
    public record Frame(float[] invViewProj, float[] prevViewProj, float deltaX, float deltaY, float deltaZ,
                        float jitterX, float jitterY, boolean reset) {
    }

    /** Advances one frame. {@code viewProj} must be unjittered. */
    public Frame next(final float[] viewProj, final double camX, final double camY, final double camZ,
                      final int renderWidth, final int renderHeight) {
        final double dx = camX - prevX;
        final double dy = camY - prevY;
        final double dz = camZ - prevZ;
        final boolean reset = prevViewProj == null || renderWidth != prevWidth || renderHeight != prevHeight
                || dx * dx + dy * dy + dz * dz > TELEPORT_DISTANCE_SQ;
        frameIndex = frameIndex % CYCLE + 1;
        final float jx = FrameMath.halton(frameIndex, 2) - 0.5f;
        final float jy = FrameMath.halton(frameIndex, 3) - 0.5f;
        final Frame frame = new Frame(FrameMath.invert(viewProj), reset ? viewProj.clone() : prevViewProj,
                reset ? 0.0f : (float) dx, reset ? 0.0f : (float) dy, reset ? 0.0f : (float) dz, jx, jy, reset);
        prevViewProj = viewProj.clone();
        prevX = camX;
        prevY = camY;
        prevZ = camZ;
        prevWidth = renderWidth;
        prevHeight = renderHeight;
        return frame;
    }

    /** Forces the next frame to reset MetalFX history (upscaler toggled, world changed, ...). */
    public void invalidate() {
        prevViewProj = null;
    }
}
