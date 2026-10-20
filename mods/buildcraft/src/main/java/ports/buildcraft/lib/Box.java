package ports.buildcraft.lib;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.phys.AABB;

/** Upstream {@code buildcraft.lib.misc.data.Box}: an inclusive block-aligned box. */
public final class Box {
    @Nullable private BlockPos min;
    @Nullable private BlockPos max;

    public Box() {}

    public Box(BlockPos a, BlockPos b) {
        set(a, b);
    }

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

    /** Upstream {@code setMin}: the max grows to stay at or above the new min. */
    public void setMin(BlockPos newMin) {
        min = newMin;
        max = max == null ? newMin : new BlockPos(Math.max(newMin.getX(), max.getX()), Math.max(newMin.getY(), max.getY()), Math.max(newMin.getZ(), max.getZ()));
    }

    /** Grows the box, or starts it, so that it contains {@code p}. */
    public void extendToEncompass(BlockPos p) {
        if (!isInitialized()) set(p, p);
        else set(new BlockPos(Math.min(min.getX(), p.getX()), Math.min(min.getY(), p.getY()), Math.min(min.getZ(), p.getZ())),
                new BlockPos(Math.max(max.getX(), p.getX()), Math.max(max.getY(), p.getY()), Math.max(max.getZ(), p.getZ())));
    }

    public Box expand(int amount) {
        if (isInitialized()) set(min.offset(-amount, -amount, -amount), max.offset(amount, amount, amount));
        return this;
    }

    /** The box's extent in blocks along each axis, counting both ends. */
    public BlockPos size() {
        return isInitialized() ? max.subtract(min).offset(1, 1, 1) : BlockPos.ZERO;
    }

    /** Upstream {@code getBoundingBox}: the box's blocks as one AABB. */
    public AABB boundingBox() {
        return new AABB(min, max.offset(1, 1, 1));
    }

    public boolean contains(BlockPos p) {
        return isInitialized()
                && p.getX() >= min.getX() && p.getX() <= max.getX()
                && p.getY() >= min.getY() && p.getY() <= max.getY()
                && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
    }

    /** True for positions on one of the box's twelve edges. */
    public boolean isOnEdge(BlockPos p) {
        return boundsMatched(p) >= 2;
    }

    /** True for the box's eight corners. */
    public boolean isCorner(BlockPos p) {
        return boundsMatched(p) == 3;
    }

    /** Upstream {@code PositionUtil.getBoxAxisCount}: how many of {@code p}'s coordinates lie on the box's bounds, or 0 outside it. */
    private int boundsMatched(BlockPos p) {
        if (!contains(p)) return 0;
        int bounds = 0;
        if (p.getX() == min.getX() || p.getX() == max.getX()) bounds++;
        if (p.getY() == min.getY() || p.getY() == max.getY()) bounds++;
        if (p.getZ() == min.getZ() || p.getZ() == max.getZ()) bounds++;
        return bounds;
    }

    /** Upstream {@code PositionUtil.getCorners}: the distinct corners of the box. */
    public Set<BlockPos> corners() {
        Set<BlockPos> corners = new LinkedHashSet<>();
        if (!isInitialized()) return corners;
        for (int x : new int[] {min.getX(), max.getX()}) {
            for (int y : new int[] {min.getY(), max.getY()}) {
                for (int z : new int[] {min.getZ(), max.getZ()}) corners.add(new BlockPos(x, y, z));
            }
        }
        return corners;
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
