package ports.buildcraft.lib;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/** Upstream {@code buildcraft.lib.misc.data.Box}: an inclusive block-aligned box. */
public final class Box {
    @Nullable private BlockPos min;
    @Nullable private BlockPos max;

    public boolean isInitialized() {
        return min != null && max != null;
    }

    public void reset() {
        min = null;
        max = null;
    }

    public void set(BlockPos a, BlockPos b) {
        min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public BlockPos min() { return min; }
    public BlockPos max() { return max; }

    public boolean contains(BlockPos p) {
        return isInitialized()
                && p.getX() >= min.getX() && p.getX() <= max.getX()
                && p.getY() >= min.getY() && p.getY() <= max.getY()
                && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
    }

    /** True for positions on one of the box's twelve edges. */
    public boolean isOnEdge(BlockPos p) {
        if (!contains(p)) return false;
        int bounds = 0;
        if (p.getX() == min.getX() || p.getX() == max.getX()) bounds++;
        if (p.getY() == min.getY() || p.getY() == max.getY()) bounds++;
        if (p.getZ() == min.getZ() || p.getZ() == max.getZ()) bounds++;
        return bounds >= 2;
    }

    public int getBlocksOnEdgeCount() {
        int sx = max.getX() - min.getX() + 1;
        int sy = max.getY() - min.getY() + 1;
        int sz = max.getZ() - min.getZ() + 1;
        return 4 * (sx + sy + sz) - 16;
    }

    public List<BlockPos> getBlocksInArea() {
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(min, max)) blocks.add(p.immutable());
        return blocks;
    }

    public BlockPos closestInsideTo(BlockPos p) {
        return new BlockPos(
                Math.max(min.getX(), Math.min(max.getX(), p.getX())),
                Math.max(min.getY(), Math.min(max.getY(), p.getY())),
                Math.max(min.getZ(), Math.min(max.getZ(), p.getZ())));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (isInitialized()) {
            tag.put("min", NbtUtils.writeBlockPos(min));
            tag.put("max", NbtUtils.writeBlockPos(max));
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.contains("min") && tag.contains("max")) set(NbtUtils.readBlockPos(tag.getCompound("min")), NbtUtils.readBlockPos(tag.getCompound("max")));
        else reset();
    }
}
