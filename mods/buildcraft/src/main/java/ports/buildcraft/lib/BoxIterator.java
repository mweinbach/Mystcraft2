package ports.buildcraft.lib;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/**
 * Upstream {@code BoxIterator} with an {@code AxisOrder}: walks a box along {@code first}, then
 * {@code second}, then {@code third}. Inverting iterators snake back and forth instead of jumping.
 */
public final class BoxIterator {
    private final BlockPos min;
    private final BlockPos max;
    private final boolean invert;
    private Direction first;
    private Direction second;
    private final Direction third;
    @Nullable private BlockPos current;

    public BoxIterator(BlockPos min, BlockPos max, Direction first, Direction second, Direction third, boolean invert) {
        this.min = min;
        this.max = max;
        this.first = first;
        this.second = second;
        this.third = third;
        this.invert = invert;
        this.current = getStart();
    }

    private BlockPos getStart() {
        BlockPos pos = BlockPos.ZERO;
        pos = replace(pos, first);
        pos = replace(pos, second);
        return replace(pos, third);
    }

    private BlockPos replace(BlockPos pos, Direction facing) {
        BlockPos with = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? min : max;
        return with(pos, facing.getAxis(), with.get(facing.getAxis()));
    }

    private static BlockPos with(BlockPos pos, Direction.Axis axis, int value) {
        return switch (axis) {
            case X -> new BlockPos(value, pos.getY(), pos.getZ());
            case Y -> new BlockPos(pos.getX(), value, pos.getZ());
            case Z -> new BlockPos(pos.getX(), pos.getY(), value);
        };
    }

    @Nullable
    public BlockPos getCurrent() {
        return current;
    }

    public boolean hasNext() {
        return current != null;
    }

    /** Moves to the next position and returns it, or null once the box is exhausted. */
    @Nullable
    public BlockPos advance() {
        if (current == null) {
            current = getStart();
            return current;
        }
        current = current.relative(first);
        if (shouldReset(first)) {
            if (invert) first = first.getOpposite();
            current = replace(current, first);
            current = current.relative(second);
            if (shouldReset(second)) {
                if (invert) second = second.getOpposite();
                current = replace(current, second);
                current = current.relative(third);
                if (shouldReset(third)) current = null;
            }
        }
        return current;
    }

    private boolean shouldReset(Direction facing) {
        int value = current.get(facing.getAxis());
        if (facing.getAxisDirection() == Direction.AxisDirection.POSITIVE) return value > max.get(facing.getAxis());
        return value < min.get(facing.getAxis());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("min", NbtUtils.writeBlockPos(min));
        tag.put("max", NbtUtils.writeBlockPos(max));
        tag.putBoolean("invert", invert);
        tag.putString("first", first.getSerializedName());
        tag.putString("second", second.getSerializedName());
        tag.putString("third", third.getSerializedName());
        if (current != null) tag.put("current", NbtUtils.writeBlockPos(current));
        return tag;
    }

    @Nullable
    public static BoxIterator load(CompoundTag tag) {
        if (!tag.contains("min") || !tag.contains("max")) return null;
        Direction first = Direction.byName(tag.getString("first"));
        Direction second = Direction.byName(tag.getString("second"));
        Direction third = Direction.byName(tag.getString("third"));
        if (first == null || second == null || third == null) return null;
        BoxIterator iterator = new BoxIterator(NbtUtils.readBlockPos(tag.getCompound("min")), NbtUtils.readBlockPos(tag.getCompound("max")),
                first, second, third, tag.getBoolean("invert"));
        iterator.current = tag.contains("current") ? NbtUtils.readBlockPos(tag.getCompound("current")) : null;
        return iterator;
    }
}
