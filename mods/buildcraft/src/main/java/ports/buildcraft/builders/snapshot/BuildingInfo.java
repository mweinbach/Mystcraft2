package ports.buildcraft.builders.snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import ports.buildcraft.lib.Box;

/**
 * Upstream {@code Snapshot.BuildingInfo}: where a snapshot of {@code size} blocks sits in the world,
 * and the box it covers there. Snapshot positions are local, from 0 to {@code size - 1}.
 */
public final class BuildingInfo {
    public final BlockPos size;
    public final BlockPos offsetPos;
    public final Rotation rotation;
    public final Box box = new Box();

    public BuildingInfo(BlockPos basePos, BlockPos offset, BlockPos size, Rotation rotation) {
        this.size = size;
        this.offsetPos = basePos.offset(offset.rotate(rotation));
        this.rotation = rotation;
        box.extendToEncompass(toWorld(BlockPos.ZERO));
        box.extendToEncompass(toWorld(size.offset(-1, -1, -1)));
    }

    public BlockPos toWorld(BlockPos local) {
        return local.rotate(rotation).offset(offsetPos);
    }

    public BlockPos fromWorld(BlockPos world) {
        return world.subtract(offsetPos).rotate(invert(rotation));
    }

    /** Upstream {@code Snapshot.posToIndex} of a world position. */
    public int posToIndex(BlockPos world) {
        BlockPos local = fromWorld(world);
        return ((local.getZ() * size.getY()) + local.getY()) * size.getX() + local.getX();
    }

    public BlockPos indexToPos(int i) {
        return toWorld(new BlockPos(i % size.getX(), (i / size.getX()) % size.getY(), i / (size.getY() * size.getX())));
    }

    public int volume() {
        return size.getX() * size.getY() * size.getZ();
    }

    private static Rotation invert(Rotation rotation) {
        return switch (rotation) {
            case NONE -> Rotation.NONE;
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case CLOCKWISE_180 -> Rotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
        };
    }
}
