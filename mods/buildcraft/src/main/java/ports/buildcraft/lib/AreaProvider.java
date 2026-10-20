package ports.buildcraft.lib;

import net.minecraft.core.BlockPos;

/** Upstream {@code IAreaProvider}: a block entity marking out an area that machines can take over. */
public interface AreaProvider {
    BlockPos min();

    BlockPos max();

    /** Removes the blocks that marked the area, once a machine has taken it. */
    void removeFromWorld();
}
