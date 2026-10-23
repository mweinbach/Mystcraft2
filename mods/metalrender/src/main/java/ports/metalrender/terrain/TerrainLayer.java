/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.terrain;

/**
 * The chunk layers Metal draws. Translucent and tripwire stay in vanilla OpenGL so they sort and
 * blend against entities and particles exactly as vanilla does.
 */
public enum TerrainLayer {
    SOLID(0, Group.SOLID),
    CUTOUT_MIPPED(1, Group.CUTOUT),
    CUTOUT(2, Group.CUTOUT);

    /** Contiguous per-section GPU ranges, matching the SectionGpuMeta solid/cutout ranges. */
    public enum Group {
        SOLID, CUTOUT;

        /** Layers stored in this range, in the order they are laid out. */
        public TerrainLayer[] layers() {
            return switch (this) {
                case SOLID -> new TerrainLayer[] {TerrainLayer.SOLID};
                case CUTOUT -> new TerrainLayer[] {TerrainLayer.CUTOUT_MIPPED, TerrainLayer.CUTOUT};
            };
        }
    }

    /** MR_MODE_* value written into every vertex of this layer. */
    public final int mode;
    public final Group group;

    TerrainLayer(final int mode, final Group group) {
        this.mode = mode;
        this.group = group;
    }
}
