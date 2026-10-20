package ports.buildcraft.builders.snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

/** Upstream {@code SchematicBlockAir}: the lowest-priority schematic, recorded for anything no other one accepts. */
public final class SchematicBlockAir implements SchematicBlock {
    public static boolean predicate(SchematicBlockContext context) {
        return true;
    }

    @Override
    public void init(SchematicBlockContext context) {}

    @Override
    public boolean isAir() {
        return true;
    }

    @Override
    public SchematicBlockAir getRotated(Rotation rotation) {
        return SchematicBlockManager.createCleanCopy(this);
    }

    @Override
    public boolean canBuild(Level level, BlockPos pos) {
        return false;
    }

    @Override
    public boolean build(Level level, BlockPos pos) {
        return true;
    }

    @Override
    public boolean buildWithoutChecks(Level level, BlockPos pos) {
        return true;
    }

    @Override
    public boolean isBuilt(Level level, BlockPos pos) {
        return true;
    }

    @Override
    public CompoundTag serializeNBT() {
        return new CompoundTag();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {}

    @Override
    public boolean equals(Object o) {
        return this == o || o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
