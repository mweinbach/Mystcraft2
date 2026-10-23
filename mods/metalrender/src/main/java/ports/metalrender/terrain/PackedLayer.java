/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.terrain;

import java.nio.ByteBuffer;
import javax.annotation.Nullable;

/**
 * One chunk layer converted to the 24-byte Metal vertex format, ready to be copied into the arena
 * on the render thread.
 *
 * @param data   little-endian packed vertices ({@code quads * QUAD_BYTES})
 * @param quads  quad count
 * @param bounds local-space AABB {minX, minY, minZ, maxX, maxY, maxZ}, or null when empty
 * @param meshlets {@code MeshletGpuMeta} templates with layer-relative base vertices
 *                 ({@code meshletCount * MESHLET_BYTES})
 * @param meshletCount number of meshlet templates
 */
public record PackedLayer(ByteBuffer data, int quads, @Nullable float[] bounds, ByteBuffer meshlets,
                          int meshletCount) {
}
