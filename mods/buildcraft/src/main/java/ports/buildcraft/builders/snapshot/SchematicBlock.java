package ports.buildcraft.builders.snapshot;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import ports.buildcraft.lib.FluidAmount;

/** Upstream {@code ISchematicBlock}: how one recorded block is read from the world, costed and rebuilt. */
public interface SchematicBlock {
    void init(SchematicBlockContext context);

    default boolean isAir() {
        return false;
    }

    /** Offsets, relative to this block, of blocks that must be in place before this one can be built. */
    default Set<BlockPos> getRequiredBlockOffsets() {
        return Set.of();
    }

    default List<ItemStack> computeRequiredItems() {
        return List.of();
    }

    default List<FluidAmount> computeRequiredFluids() {
        return List.of();
    }

    SchematicBlock getRotated(Rotation rotation);

    boolean canBuild(Level level, BlockPos pos);

    default boolean isReadyToBuild(Level level, BlockPos pos) {
        return true;
    }

    boolean build(Level level, BlockPos pos);

    boolean buildWithoutChecks(Level level, BlockPos pos);

    boolean isBuilt(Level level, BlockPos pos);

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag tag) throws InvalidSnapshotException;
}
