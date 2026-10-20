package ports.buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import ports.buildcraft.lib.FluidAmount;

/**
 * Upstream {@code Blueprint.BuildingInfo}: a blueprint placed at a base position and rotation, with its
 * palette rotated to match and what each block and entity costs to build.
 */
public final class BlueprintBuildingInfo {
    public final Blueprint blueprint;
    public final BuildingInfo info;
    public final List<SchematicBlock> rotatedPalette;
    private final List<List<ItemStack>> toPlaceRequiredItems;
    private final List<List<FluidAmount>> toPlaceRequiredFluids;
    public final List<SchematicEntity> entities;
    private final Map<SchematicEntity, List<ItemStack>> entitiesRequiredItems = new IdentityHashMap<>();
    private final Map<SchematicEntity, List<FluidAmount>> entitiesRequiredFluids = new IdentityHashMap<>();

    public BlueprintBuildingInfo(Blueprint blueprint, BlockPos basePos, Rotation rotation) {
        this.blueprint = blueprint;
        this.info = new BuildingInfo(basePos, blueprint.offset, blueprint.size, rotation);
        List<SchematicBlock> palette = new ArrayList<>();
        for (SchematicBlock schematicBlock : blueprint.palette) palette.add(schematicBlock.getRotated(rotation));
        rotatedPalette = List.copyOf(palette);
        int size = blueprint.getDataSize();
        List<List<ItemStack>> items = new ArrayList<>(Collections.nCopies(size, null));
        List<List<FluidAmount>> fluids = new ArrayList<>(Collections.nCopies(size, null));
        for (int i = 0; i < size; i++) {
            SchematicBlock schematicBlock = rotatedPalette.get(blueprint.data[i]);
            if (schematicBlock.isAir()) continue;
            items.set(i, schematicBlock.computeRequiredItems());
            fluids.set(i, schematicBlock.computeRequiredFluids());
        }
        toPlaceRequiredItems = items;
        toPlaceRequiredFluids = fluids;
        List<SchematicEntity> rotatedEntities = new ArrayList<>();
        for (SchematicEntity schematicEntity : blueprint.entities) {
            SchematicEntity rotated = schematicEntity.getRotated(rotation);
            rotatedEntities.add(rotated);
            entitiesRequiredItems.put(rotated, schematicEntity.computeRequiredItems());
            entitiesRequiredFluids.put(rotated, schematicEntity.computeRequiredFluids());
        }
        entities = List.copyOf(rotatedEntities);
    }

    /** The rotated schematic for a world position, or null outside the box. */
    @Nullable
    public SchematicBlock schematicAt(BlockPos world) {
        return info.box.contains(world) ? rotatedPalette.get(blueprint.data[info.posToIndex(world)]) : null;
    }

    /** Null for air. */
    @Nullable
    public List<ItemStack> toPlaceRequiredItems(int index) {
        return toPlaceRequiredItems.get(index);
    }

    /** Null for air. */
    @Nullable
    public List<FluidAmount> toPlaceRequiredFluids(int index) {
        return toPlaceRequiredFluids.get(index);
    }

    public List<ItemStack> entityRequiredItems(SchematicEntity schematicEntity) {
        return entitiesRequiredItems.get(schematicEntity);
    }

    public List<FluidAmount> entityRequiredFluids(SchematicEntity schematicEntity) {
        return entitiesRequiredFluids.get(schematicEntity);
    }
}
