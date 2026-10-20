package ports.buildcraft.builders.snapshot;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.lib.FluidAmount;

/** Upstream {@code ISchematicEntity}: one recorded entity, positioned relative to the scanned box. */
public interface SchematicEntity {
    void init(SchematicEntityContext context);

    Vec3 getPos();

    default List<ItemStack> computeRequiredItems() {
        return List.of();
    }

    default List<FluidAmount> computeRequiredFluids() {
        return List.of();
    }

    SchematicEntity getRotated(Rotation rotation);

    @Nullable
    Entity build(Level level, BlockPos basePos);

    @Nullable
    Entity buildWithoutChecks(Level level, BlockPos basePos);

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag tag) throws InvalidSnapshotException;
}
