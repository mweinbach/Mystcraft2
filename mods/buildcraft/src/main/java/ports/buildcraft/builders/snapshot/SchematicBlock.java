package ports.buildcraft.builders.snapshot;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
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

    /** The block a snapshot preview draws here; upstream built each schematic into a fake world to draw it. */
    default BlockState getPreviewState() {
        return Blocks.AIR.defaultBlockState();
    }

    /** The data of the block entity a preview draws with {@link #getPreviewState}, if any. */
    @Nullable
    default CompoundTag getPreviewTileNbt() {
        return null;
    }

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag tag) throws InvalidSnapshotException;

    /**
     * Not upstream. The schematic a scan of the block this one describes would record, rebuilt from the rules
     * (see {@link SchematicSafety}); saved data is not trusted, since clients upload snapshots. Throws when no
     * scan could have recorded this one: a block that must not be built, or a placed block other than the one
     * the rules derive from the recorded (and costed) state.
     */
    default SchematicBlock canonicalize() throws InvalidSnapshotException {
        return this;
    }
}
