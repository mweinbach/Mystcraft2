/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.terrain;

import java.util.BitSet;

/**
 * Which parts of the block atlas are fully opaque, on a grid of 16x16-pixel cells.
 *
 * <p>A cell is opaque when it overlaps at least one sprite whose every pixel (all animation frames)
 * has alpha 255, and no sprite with any other alpha. A cutout quad whose texture rectangle lies only
 * in opaque cells can never fail the alpha test, so the GPU can draw it without
 * {@code discard_fragment}, which keeps hidden-surface removal on for it. Cells shared with a
 * transparent sprite are not opaque, so the check is conservative.
 */
public final class OpaqueSprites {

    public static final int CELL = 16;
    private static final float EDGE = 1.0e-3f;
    public static final OpaqueSprites NONE = new OpaqueSprites(1, 1, 1, 1, new BitSet());

    private static volatile OpaqueSprites current = NONE;

    private final int atlasWidth;
    private final int atlasHeight;
    private final int cellsX;
    private final int cellsY;
    private final BitSet opaque;

    private OpaqueSprites(final int atlasWidth, final int atlasHeight, final int cellsX, final int cellsY,
                          final BitSet opaque) {
        this.atlasWidth = atlasWidth;
        this.atlasHeight = atlasHeight;
        this.cellsX = cellsX;
        this.cellsY = cellsY;
        this.opaque = opaque;
    }

    /** The block atlas map used by the chunk workers; {@link #NONE} until the atlas loads. */
    public static OpaqueSprites current() {
        return current;
    }

    public static void setCurrent(final OpaqueSprites map) {
        current = map;
    }

    /** Collects sprite rectangles (atlas pixels) for one atlas. */
    public static final class Builder {
        private final int width;
        private final int height;
        private final int cellsX;
        private final int cellsY;
        private final BitSet opaque;
        private final BitSet transparent;

        public Builder(final int width, final int height) {
            this.width = width;
            this.height = height;
            this.cellsX = Math.max(1, (width + CELL - 1) / CELL);
            this.cellsY = Math.max(1, (height + CELL - 1) / CELL);
            this.opaque = new BitSet(cellsX * cellsY);
            this.transparent = new BitSet(cellsX * cellsY);
        }

        public Builder sprite(final int x, final int y, final int w, final int h, final boolean fullyOpaque) {
            final int cx0 = Math.max(0, x / CELL);
            final int cy0 = Math.max(0, y / CELL);
            final int cx1 = Math.min(cellsX - 1, (x + w - 1) / CELL);
            final int cy1 = Math.min(cellsY - 1, (y + h - 1) / CELL);
            for (int cy = cy0; cy <= cy1; cy++) {
                for (int cx = cx0; cx <= cx1; cx++) {
                    (fullyOpaque ? opaque : transparent).set(cy * cellsX + cx);
                }
            }
            return this;
        }

        public OpaqueSprites build() {
            final BitSet bits = (BitSet) opaque.clone();
            bits.andNot(transparent);
            return new OpaqueSprites(width, height, cellsX, cellsY, bits);
        }
    }

    /** True when every texel in the UV rectangle lies in an opaque cell. */
    public boolean isOpaque(final float u0, final float v0, final float u1, final float v1) {
        if (this == NONE || !(u0 >= 0.0f && v0 >= 0.0f && u1 <= 1.0f && v1 <= 1.0f)) {
            return false;
        }
        // Texels touched: floor(u0 * w) up to the texel just before u1 * w (nearest sampling), with
        // a 1/1000 texel tolerance so float error at a sprite edge does not reach the next cell.
        final int x0 = (int) Math.floor(u0 * atlasWidth + EDGE);
        final int y0 = (int) Math.floor(v0 * atlasHeight + EDGE);
        final int x1 = Math.max(x0, (int) Math.ceil(u1 * atlasWidth - EDGE) - 1);
        final int y1 = Math.max(y0, (int) Math.ceil(v1 * atlasHeight - EDGE) - 1);
        final int cx1 = Math.min(cellsX - 1, x1 / CELL);
        final int cy1 = Math.min(cellsY - 1, y1 / CELL);
        for (int cy = y0 / CELL; cy <= cy1; cy++) {
            for (int cx = x0 / CELL; cx <= cx1; cx++) {
                if (!opaque.get(cy * cellsX + cx)) {
                    return false;
                }
            }
        }
        return true;
    }
}
