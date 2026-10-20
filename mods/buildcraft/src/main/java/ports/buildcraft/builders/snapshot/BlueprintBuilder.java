package ports.buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.lib.FluidAmount;

/**
 * Upstream {@code BlueprintBuilder}: builds each block of a blueprint from the items and fluids its schematic
 * costs, once the blocks it rests on are in place, then spawns the blueprint's entities. With a full battery
 * it removes entities in the box that the blueprint does not have.
 */
public final class BlueprintBuilder extends SnapshotBuilder<BlueprintBuilder.Host> {
    private static final double MAX_ENTITY_DISTANCE = 0.1D;
    private static final String FLUID_STACK_KEY = "BuilderFluidStack";

    /** Upstream {@code ITileForBlueprintBuilder}. */
    public interface Host extends SnapshotHost {
        @Nullable
        BlueprintBuildingInfo blueprintBuildingInfo();

        /** Upstream {@code extract(filter, count, count, simulate)}: exactly {@code count} matching items, or nothing. */
        ItemStack extractItems(Predicate<ItemStack> filter, int count, boolean simulate);

        /** Puts an item back into the resources; returns what did not fit. */
        ItemStack insertResource(ItemStack stack);

        /** Where items that no longer fit in the resources go. */
        void dropResource(ItemStack stack);

        /** Drains up to the given amount of that fluid from the tanks. */
        FluidAmount drainFluid(FluidAmount fluid, boolean simulate);

        /** Fills the tanks; returns the amount accepted. */
        long fillFluid(FluidAmount fluid);
    }

    private record Required(List<ItemStack> items, List<FluidAmount> fluids) {}

    @Nullable private List<List<ItemStack>> remainingDisplayRequiredBlocks;
    private List<ItemStack> remainingDisplayRequiredBlocksConcat = Collections.emptyList();
    public final List<ItemStack> remainingDisplayRequired = new ArrayList<>();
    private final Map<Required, Optional<List<ItemStack>>> extractRequiredCache = new HashMap<>();

    public BlueprintBuilder(Host host) {
        super(host);
    }

    @Nullable
    @Override
    protected BuildingInfo buildingInfo() {
        BlueprintBuildingInfo info = host.blueprintBuildingInfo();
        return info == null ? null : info.info;
    }

    @Nullable
    private SchematicBlock getSchematicBlock(BlockPos pos) {
        BlueprintBuildingInfo info = host.blueprintBuildingInfo();
        return info == null ? null : info.schematicAt(pos);
    }

    @Override
    protected boolean isAir(BlockPos pos) {
        SchematicBlock schematicBlock = getSchematicBlock(pos);
        return schematicBlock == null || schematicBlock.isAir();
    }

    @Override
    public void updateSnapshot() {
        super.updateSnapshot();
        BuildingInfo info = buildingInfo();
        if (info == null) return;
        remainingDisplayRequiredBlocks = new ArrayList<>(Collections.nCopies(info.volume(), List.of()));
    }

    @Override
    public void resourcesChanged() {
        super.resourcesChanged();
        extractRequiredCache.clear();
    }

    @Override
    public void cancel() {
        super.cancel();
        remainingDisplayRequiredBlocks = null;
    }

    private static Stream<ItemStack> getDisplayRequired(@Nullable List<ItemStack> requiredItems, @Nullable List<FluidAmount> requiredFluids) {
        return Stream.concat(
                requiredItems == null ? Stream.empty() : requiredItems.stream(),
                requiredFluids == null ? Stream.empty() : requiredFluids.stream().map(fluid -> new ItemStack(bucketFor(fluid.fluid()))));
    }

    /** Upstream {@code FluidUtil.getFilledBucket}; fluids without a bucket of their own show as an empty bucket. */
    private static Item bucketFor(Fluid fluid) {
        Item bucket = fluid.getBucket();
        return bucket == Items.AIR ? Items.BUCKET : bucket;
    }

    private Optional<List<ItemStack>> tryExtractRequired(@Nullable List<ItemStack> requiredItems, @Nullable List<FluidAmount> requiredFluids,
            boolean simulate) {
        List<ItemStack> items = requiredItems == null ? List.of() : requiredItems;
        List<FluidAmount> fluids = requiredFluids == null ? List.of() : requiredFluids;
        if (!simulate) return extractRequired(items, fluids, false);
        return extractRequiredCache.computeIfAbsent(new Required(items, fluids), required -> extractRequired(items, fluids, true));
    }

    private Optional<List<ItemStack>> extractRequired(List<ItemStack> items, List<FluidAmount> fluids, boolean simulate) {
        boolean hasItems = mergeSameItems(items).stream()
                .noneMatch(stack -> host.extractItems(extracted -> ItemStack.isSameItemSameTags(stack, extracted), stack.getCount(), true).isEmpty());
        boolean hasFluids = mergeSameFluids(fluids).stream()
                .allMatch(fluid -> host.drainFluid(fluid, true).equals(fluid));
        if (!hasItems || !hasFluids) return Optional.empty();
        List<ItemStack> extracted = new ArrayList<>();
        for (ItemStack stack : items) {
            extracted.add(host.extractItems(candidate -> ItemStack.isSameItemSameTags(stack, candidate), stack.getCount(), simulate));
        }
        for (FluidAmount fluid : mergeSameFluids(fluids)) {
            FluidAmount drained = host.drainFluid(fluid, simulate);
            ItemStack bucket = new ItemStack(bucketFor(drained.fluid()));
            CompoundTag fluidTag = new CompoundTag();
            fluidTag.putString("fluid", Registry.FLUID.getKey(drained.fluid()).toString());
            fluidTag.putLong("amount", drained.amount());
            bucket.getOrCreateTag().put(FLUID_STACK_KEY, fluidTag);
            extracted.add(bucket);
        }
        return Optional.of(mergeSameItems(extracted));
    }

    @Override
    protected boolean canPlace(BlockPos pos) {
        SchematicBlock schematicBlock = getSchematicBlock(pos);
        return schematicBlock != null && !schematicBlock.isAir() && schematicBlock.canBuild(host.hostLevel(), pos);
    }

    @Override
    protected boolean isReadyToPlace(BlockPos pos) {
        SchematicBlock schematicBlock = getSchematicBlock(pos);
        if (schematicBlock == null) return false;
        for (BlockPos offset : schematicBlock.getRequiredBlockOffsets()) {
            BlockPos required = pos.offset(offset);
            if (getSchematicBlock(required) != null && checkResults[posToIndex(required)] != CHECK_RESULT_CORRECT) return false;
        }
        return schematicBlock.isReadyToBuild(host.hostLevel(), pos);
    }

    @Override
    protected boolean hasEnoughToPlaceItems(BlockPos pos) {
        BlueprintBuildingInfo info = host.blueprintBuildingInfo();
        int index = posToIndex(pos);
        return tryExtractRequired(info.toPlaceRequiredItems(index), info.toPlaceRequiredFluids(index), true).isPresent();
    }

    @Nullable
    @Override
    protected List<ItemStack> getToPlaceItems(BlockPos pos) {
        BlueprintBuildingInfo info = host.blueprintBuildingInfo();
        int index = posToIndex(pos);
        return tryExtractRequired(info.toPlaceRequiredItems(index), info.toPlaceRequiredFluids(index), false).orElse(null);
    }

    @Override
    protected void cancelPlaceTask(PlaceTask placeTask) {
        super.cancelPlaceTask(placeTask);
        for (ItemStack stack : placeTask.items) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains(FLUID_STACK_KEY, Tag.TAG_COMPOUND)) {
                CompoundTag fluidTag = tag.getCompound(FLUID_STACK_KEY);
                ResourceLocation id = ResourceLocation.tryParse(fluidTag.getString("fluid"));
                Fluid fluid = id == null ? Fluids.EMPTY : Registry.FLUID.get(id);
                if (fluid != Fluids.EMPTY) host.fillFluid(new FluidAmount(fluid, fluidTag.getLong("amount") * stack.getCount()));
            } else {
                ItemStack leftover = host.insertResource(stack.copy());
                if (!leftover.isEmpty()) host.dropResource(leftover);
            }
        }
    }

    @Override
    protected boolean isBlockCorrect(BlockPos pos) {
        SchematicBlock schematicBlock = getSchematicBlock(pos);
        return schematicBlock != null && schematicBlock.isBuilt(host.hostLevel(), pos);
    }

    @Override
    protected boolean doPlaceTask(PlaceTask placeTask) {
        SchematicBlock schematicBlock = getSchematicBlock(placeTask.pos);
        return schematicBlock != null && schematicBlock.build(host.hostLevel(), placeTask.pos);
    }

    @Override
    public boolean tick() {
        BlueprintBuildingInfo info = host.blueprintBuildingInfo();
        if (info == null) return super.tick();
        Level level = host.hostLevel();
        Vec3 offset = Vec3.atLowerCornerOf(info.info.offsetPos);
        List<Entity> entitiesWithinBox = level.getEntitiesOfClass(Entity.class, info.info.box.boundingBox(), entity -> entity != null);
        List<SchematicEntity> toSpawn = info.entities.stream()
                .filter(schematicEntity -> {
                    Vec3 target = schematicEntity.getPos().add(offset);
                    return entitiesWithinBox.stream().noneMatch(entity -> entity.position().distanceTo(target) < MAX_ENTITY_DISTANCE);
                })
                .toList();
        List<ItemStack> required = new ArrayList<>(remainingDisplayRequiredBlocksConcat);
        for (SchematicEntity schematicEntity : toSpawn) {
            getDisplayRequired(info.entityRequiredItems(schematicEntity), info.entityRequiredFluids(schematicEntity)).forEach(required::add);
        }
        remainingDisplayRequired.clear();
        remainingDisplayRequired.addAll(mergeSameItems(required));
        List<Entity> toKill = entitiesWithinBox.stream()
                .filter(entity -> info.entities.stream()
                        .map(schematicEntity -> schematicEntity.getPos().add(offset))
                        .noneMatch(target -> entity.position().distanceTo(target) < MAX_ENTITY_DISTANCE)
                        && SchematicEntityManager.getSchematicEntity(new SchematicEntityContext(level, BlockPos.ZERO, entity)) != null)
                .toList();
        if (!toKill.isEmpty()) {
            if (!host.battery().isFull()) return false;
            toKill.forEach(Entity::discard);
        }
        if (!super.tick()) return false;
        if (!toSpawn.isEmpty()) {
            if (!host.battery().isFull()) return false;
            for (SchematicEntity schematicEntity : toSpawn) {
                List<ItemStack> items = info.entityRequiredItems(schematicEntity);
                List<FluidAmount> fluids = info.entityRequiredFluids(schematicEntity);
                if (tryExtractRequired(items, fluids, true).isPresent() && schematicEntity.build(level, info.info.offsetPos) != null) {
                    tryExtractRequired(items, fluids, false);
                }
            }
        }
        return true;
    }

    @Override
    protected boolean check(BlockPos pos) {
        if (!super.check(pos)) return false;
        if (remainingDisplayRequiredBlocks != null) {
            BlueprintBuildingInfo info = host.blueprintBuildingInfo();
            int index = posToIndex(pos);
            remainingDisplayRequiredBlocks.set(index, checkResults[index] != CHECK_RESULT_CORRECT
                    ? getDisplayRequired(info.toPlaceRequiredItems(index), info.toPlaceRequiredFluids(index)).toList()
                    : List.of());
        }
        return true;
    }

    @Override
    protected void afterChecks() {
        if (remainingDisplayRequiredBlocks == null) return;
        remainingDisplayRequiredBlocksConcat = mergeSameItems(remainingDisplayRequiredBlocks.stream().flatMap(List::stream).toList());
    }

    /** Upstream sent the stacks with their counts beside them, since a merged count can pass a stack's size. */
    @Override
    public void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        ListTag list = new ListTag();
        for (ItemStack stack : remainingDisplayRequired) {
            ItemStack one = stack.copy();
            one.setCount(1);
            CompoundTag stackTag = one.save(new CompoundTag());
            stackTag.putInt("BuilderCount", stack.getCount());
            list.add(stackTag);
        }
        tag.put("remainingDisplayRequired", list);
    }

    @Override
    public void readClient(CompoundTag tag) {
        super.readClient(tag);
        remainingDisplayRequired.clear();
        for (Tag element : tag.getList("remainingDisplayRequired", Tag.TAG_COMPOUND)) {
            CompoundTag stackTag = (CompoundTag) element;
            ItemStack stack = ItemStack.of(stackTag);
            stack.setCount(stackTag.getInt("BuilderCount"));
            remainingDisplayRequired.add(stack);
        }
    }

    /** Upstream {@code StackUtil.mergeSameItems}: one stack per item and tag, with the counts summed past the stack size. */
    public static List<ItemStack> mergeSameItems(List<ItemStack> items) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack toAdd : items) {
            boolean found = false;
            for (ItemStack stack : stacks) {
                if (ItemStack.isSameItemSameTags(stack, toAdd)) {
                    stack.grow(toAdd.getCount());
                    found = true;
                }
            }
            if (!found) stacks.add(toAdd.copy());
        }
        return stacks;
    }

    /** Upstream {@code FluidUtilBC.mergeSameFluids}. */
    private static List<FluidAmount> mergeSameFluids(List<FluidAmount> fluids) {
        List<FluidAmount> merged = new ArrayList<>();
        for (FluidAmount toAdd : fluids) {
            boolean found = false;
            for (int i = 0; i < merged.size(); i++) {
                if (merged.get(i).fluid() == toAdd.fluid()) {
                    merged.set(i, merged.get(i).withAmount(merged.get(i).amount() + toAdd.amount()));
                    found = true;
                }
            }
            if (!found) merged.add(toAdd);
        }
        return merged;
    }
}
