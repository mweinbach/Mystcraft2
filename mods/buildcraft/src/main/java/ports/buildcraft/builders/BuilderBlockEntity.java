package ports.buildcraft.builders;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.builders.filler.FilledTemplate;
import ports.buildcraft.builders.snapshot.Blueprint;
import ports.buildcraft.builders.snapshot.BlueprintBuilder;
import ports.buildcraft.builders.snapshot.BlueprintBuildingInfo;
import ports.buildcraft.builders.snapshot.BuildingInfo;
import ports.buildcraft.builders.snapshot.EnumSnapshotType;
import ports.buildcraft.builders.snapshot.Snapshot;
import ports.buildcraft.builders.snapshot.SnapshotBuilder;
import ports.buildcraft.builders.snapshot.SnapshotStorage;
import ports.buildcraft.builders.snapshot.Template;
import ports.buildcraft.builders.snapshot.TemplateBuilder;
import ports.buildcraft.core.MarkerPathBlockEntity;
import ports.buildcraft.lib.BlockProtection;
import ports.buildcraft.lib.Box;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.InventoryUtil;
import ports.buildcraft.lib.MjBattery;
import ports.buildcraft.lib.PositionUtil;
import ports.buildcraft.lib.Power;
import ports.buildcraft.lib.SyncedBlockEntity;
import ports.buildcraft.lib.Tank;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.PortHolder;

/**
 * Upstream {@code TileBuilder}: builds the template or blueprint in its snapshot slot behind it, rotated to
 * its own facing, from the items in its 27 resource slots and the fluids in its four tanks. Placed against a
 * path of path marks, it takes the path and builds the snapshot at every block along it in turn.
 */
public class BuilderBlockEntity extends SyncedBlockEntity implements PortHolder, WorldlyContainer, MenuProvider,
        TemplateBuilder.Host, BlueprintBuilder.Host {
    public static final int SLOT_SNAPSHOT = 0;
    public static final int RESOURCE_SLOTS = 27;
    public static final int TANKS = 4;
    public static final int TANK_CAPACITY = 8000;
    private static final int[] SLOTS = IntStream.range(0, 1 + RESOURCE_SLOTS).toArray();

    private final NonNullList<ItemStack> items = NonNullList.withSize(1 + RESOURCE_SLOTS, ItemStack.EMPTY);
    private final MjBattery battery = new MjBattery(16_000 * Power.MJ);
    private final EnergyPort energyPort = battery.receiver(false, this::setChanged);
    final List<Tank> tanks = IntStream.rangeClosed(1, TANKS).mapToObj(i -> new Tank("tank" + i, TANK_CAPACITY)).toList();
    private final FluidPort fluidPort = new BuilderTanks();
    private boolean canExcavate = true;
    /** The player who placed the machine, if known; see {@link #owner()}. */
    @Nullable private GameProfile owner;

    /** The real path, just a few block positions. */
    @Nullable public List<BlockPos> path;
    /** The real path plus every block position in between. */
    private List<BlockPos> basePoses = new ArrayList<>();
    private int currentBasePosIndex;
    @Nullable private Snapshot snapshot;
    @Nullable public EnumSnapshotType snapshotType;
    @Nullable private BuildingInfo templateBuildingInfo;
    @Nullable private FilledTemplate template;
    @Nullable private BlueprintBuildingInfo blueprintBuildingInfo;
    public final TemplateBuilder templateBuilder = new TemplateBuilder(this);
    public final BlueprintBuilder blueprintBuilder = new BlueprintBuilder(this);
    private Box currentBox = new Box();
    @Nullable private Rotation rotation;
    private boolean isDone;
    private boolean syncedTasks;
    private int syncedLeftToBreak;
    private int syncedLeftToPlace;
    private List<ItemStack> syncedDisplay = List.of();

    final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            Tank tank = tanks.get(index / 2);
            return index % 2 == 0 ? Registry.FLUID.getId(tank.fluid()) : tank.amount();
        }

        @Override public void set(int index, int value) {}
        @Override public int getCount() { return TANKS * 2; }
    };

    public BuilderBlockEntity(BlockPos pos, BlockState state) {
        super(BCBuilders.BUILDER_TYPE.get(), pos, state);
    }

    /** Upstream {@code onPlacedBy}: a path of path marks in front of the builder's back becomes its path and the marks are removed. */
    void onPlaced() {
        Direction facing = getBlockState().getValue(BuilderBlock.FACING);
        if (level.getBlockEntity(worldPosition.relative(facing.getOpposite())) instanceof MarkerPathBlockEntity provider) {
            List<BlockPos> copiedPath = List.copyOf(provider.getPath());
            if (copiedPath.size() >= 2) {
                path = copiedPath;
                provider.removeFromWorld();
            }
        }
        updateBasePoses();
        setChanged();
        queueSync();
    }

    /** After a wrench turn a builder without a path builds behind its new back; upstream kept the old base position. */
    void onTurned() {
        updateBasePoses();
        setChanged();
        queueSync();
    }

    /** Upstream {@code TileBC_Neptune.onRemove}: in-flight items go back to the resources, which drop with the snapshot. */
    void onRemoved() {
        SnapshotBuilder<?> builder = getBuilder();
        if (builder != null) builder.cancel();
        Containers.dropContents(level, worldPosition, items);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        templateBuilder.stopListening();
        blueprintBuilder.stopListening();
    }

    private void onSnapshotSlotChanged() {
        if (level == null || level.isClientSide) return;
        currentBasePosIndex = 0;
        snapshot = loadSnapshot(items.get(SLOT_SNAPSHOT));
        updateSnapshot(true);
        setChanged();
        queueSync();
    }

    @Nullable
    private static Snapshot loadSnapshot(ItemStack stack) {
        if (!(stack.getItem() instanceof SnapshotItem)) return null;
        Snapshot.Header header = SnapshotItem.getHeader(stack);
        return header == null ? null : SnapshotStorage.get(SnapshotStorage.Side.SERVER).getSnapshot(header.key);
    }

    private void updateSnapshot(boolean canGetFacing) {
        SnapshotBuilder<?> oldBuilder = getBuilder();
        if (oldBuilder != null) oldBuilder.cancel();
        BlockPos basePos = getCurrentBasePos();
        templateBuildingInfo = null;
        template = null;
        blueprintBuildingInfo = null;
        currentBox = new Box();
        if (snapshot != null && basePos != null) {
            snapshotType = snapshot.getType();
            if (canGetFacing) {
                Direction facing = getBlockState().getValue(BuilderBlock.FACING);
                rotation = Arrays.stream(Rotation.values()).filter(r -> r.rotate(snapshot.facing) == facing).findFirst().orElse(Rotation.NONE);
            }
            Rotation placed = rotation == null ? Rotation.NONE : rotation;
            if (snapshot instanceof Template recorded) {
                templateBuildingInfo = new BuildingInfo(basePos, recorded.offset, recorded.size, placed);
                template = new FilledTemplate(recorded.size, recorded.data);
                currentBox = templateBuildingInfo.box;
            }
            if (snapshot instanceof Blueprint recorded) {
                blueprintBuildingInfo = new BlueprintBuildingInfo(recorded, basePos, placed);
                currentBox = blueprintBuildingInfo.info.box;
            }
            SnapshotBuilder<?> builder = getBuilder();
            if (builder != null) builder.updateSnapshot();
        } else {
            snapshotType = null;
            rotation = null;
        }
        updateSlotModel();
    }

    /** Upstream drew the slot from {@code getActualState}; here the block state carries it. */
    private void updateSlotModel() {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        EnumOptionalSnapshotType shown = EnumOptionalSnapshotType.fromNullable(snapshotType);
        if (state.hasProperty(BuilderBlock.SNAPSHOT_TYPE) && state.getValue(BuilderBlock.SNAPSHOT_TYPE) != shown) {
            level.setBlock(worldPosition, state.setValue(BuilderBlock.SNAPSHOT_TYPE, shown), 3);
        }
    }

    private void updateBasePoses() {
        basePoses.clear();
        if (path != null) {
            basePoses.add(path.get(0));
            for (int i = 1; i < path.size(); i++) basePoses.addAll(PositionUtil.getAllOnPath(path.get(i - 1), path.get(i)));
        } else {
            basePoses.add(worldPosition.relative(getBlockState().getValue(BuilderBlock.FACING).getOpposite()));
        }
    }

    @Nullable
    private BlockPos getCurrentBasePos() {
        return currentBasePosIndex < basePoses.size() ? basePoses.get(currentBasePosIndex) : null;
    }

    void clientTick() {
        SnapshotBuilder<?> builder = getBuilder();
        if (builder != null) builder.clientTick();
    }

    /**
     * Upstream {@code update}. Upstream meant to unlock its "paving the way" advancement after the last step of a
     * path, but the index never reaches the path's length, so it never unlocked; advancements are not ported.
     */
    void serverTick() {
        templateBuilder.startListening();
        blueprintBuilder.startListening();
        battery.tick();
        SnapshotBuilder<?> builder = getBuilder();
        if (builder != null) {
            isDone = builder.tick();
            if (isDone && currentBasePosIndex < basePoses.size() - 1) {
                currentBasePosIndex++;
                updateSnapshot(true);
                queueSync();
            }
            setChanged();
        }
        builder = getBuilder();
        boolean hasTasks = builder != null && builder.hasTasks();
        int leftToBreak = builder == null ? 0 : builder.leftToBreak;
        int leftToPlace = builder == null ? 0 : builder.leftToPlace;
        List<ItemStack> display = builder == blueprintBuilder ? blueprintBuilder.remainingDisplayRequired : List.of();
        if (hasTasks || syncedTasks || leftToBreak != syncedLeftToBreak || leftToPlace != syncedLeftToPlace || !sameStacks(display, syncedDisplay)) {
            syncedTasks = hasTasks;
            syncedLeftToBreak = leftToBreak;
            syncedLeftToPlace = leftToPlace;
            syncedDisplay = display.stream().map(ItemStack::copy).toList();
            queueSync();
        }
        flushSync();
    }

    private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    // State

    @Nullable
    public SnapshotBuilder<?> getBuilder() {
        if (snapshotType == EnumSnapshotType.TEMPLATE) return templateBuilder;
        if (snapshotType == EnumSnapshotType.BLUEPRINT) return blueprintBuilder;
        return null;
    }

    public Box getBox() {
        return currentBox;
    }

    public List<BlockPos> getBasePoses() {
        return List.copyOf(basePoses);
    }

    public int getCurrentBasePosIndex() {
        return currentBasePosIndex;
    }

    @Nullable
    public Rotation getRotation() {
        return rotation;
    }

    public boolean isDone() {
        return isDone;
    }

    /** What the blueprint still needs, as upstream's display slots show it. */
    public List<ItemStack> getDisplayRequired() {
        return snapshotType == EnumSnapshotType.BLUEPRINT ? blueprintBuilder.remainingDisplayRequired : List.of();
    }

    public List<Tank> getTanks() {
        return tanks;
    }

    // Hosts

    /** Upstream {@code TileBC_Neptune.getOwner}: the player who placed the machine, or the machine profile. */
    @Override
    public GameProfile owner() {
        return owner == null ? BlockProtection.NULL_PROFILE : owner;
    }

    /** Set when a player places the machine (upstream {@code onPlacedBy}); the machine profile counts as no owner. */
    public void setOwner(@Nullable GameProfile profile) {
        owner = profile == null || profile.getId() == null || profile.getId().equals(BlockProtection.NULL_PROFILE.getId()) ? null : profile;
        setChanged();
    }

    @Override
    public Level hostLevel() {
        return level;
    }

    @Override
    public MjBattery battery() {
        return battery;
    }

    @Override
    public BlockPos builderPos() {
        return worldPosition;
    }

    @Override
    public boolean canExcavate() {
        return canExcavate;
    }

    @Nullable
    @Override
    public BuildingInfo templateBuildingInfo() {
        return templateBuildingInfo;
    }

    @Nullable
    @Override
    public FilledTemplate template() {
        return template;
    }

    @Nullable
    @Override
    public BlueprintBuildingInfo blueprintBuildingInfo() {
        return blueprintBuildingInfo;
    }

    /** Upstream {@code invResources.extract(null, 1, 1, simulate)}: one item from the first non-empty slot. */
    @Override
    public ItemStack extractResource(boolean simulate) {
        return extractItems(stack -> true, 1, simulate);
    }

    @Override
    public ItemStack extractItems(Predicate<ItemStack> filter, int count, boolean simulate) {
        ItemStack first = ItemStack.EMPTY;
        int available = 0;
        for (int slot = 1; slot <= RESOURCE_SLOTS; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty() || !filter.test(stack)) continue;
            if (first.isEmpty()) first = stack;
            else if (!ItemStack.isSameItemSameTags(first, stack)) continue;
            available += stack.getCount();
        }
        if (first.isEmpty() || available < count) return ItemStack.EMPTY;
        ItemStack result = first.copy();
        result.setCount(count);
        if (simulate) return result;
        int left = count;
        for (int slot = 1; slot <= RESOURCE_SLOTS && left > 0; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty() || !filter.test(stack) || !ItemStack.isSameItemSameTags(result, stack)) continue;
            int taken = Math.min(left, stack.getCount());
            stack.shrink(taken);
            left -= taken;
        }
        resourcesChanged();
        return result;
    }

    @Override
    public ItemStack insertResource(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 1; slot <= RESOURCE_SLOTS && !remaining.isEmpty(); slot++) {
            ItemStack existing = items.get(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, remaining)) {
                int moved = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(moved);
                remaining.shrink(moved);
            }
        }
        for (int slot = 1; slot <= RESOURCE_SLOTS && !remaining.isEmpty(); slot++) {
            if (items.get(slot).isEmpty()) {
                int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                items.set(slot, remaining.split(moved));
            }
        }
        resourcesChanged();
        return remaining;
    }

    @Override
    public void dropResource(ItemStack stack) {
        InventoryUtil.drop(level, worldPosition, stack);
    }

    /** Upstream {@code TankManager.drain(FluidStack)}: that fluid from every tank holding it, in order. */
    @Override
    public FluidAmount drainFluid(FluidAmount fluid, boolean simulate) {
        long left = fluid.amount();
        long drained = 0;
        for (Tank tank : tanks) {
            if (left <= 0) break;
            FluidAmount part = tank.drain(fluid.fluid(), (int) Math.min(Integer.MAX_VALUE, left), simulate);
            drained += part.amount();
            left -= part.amount();
        }
        if (drained > 0 && !simulate) tanksChanged();
        return drained <= 0 ? FluidAmount.EMPTY : new FluidAmount(fluid.fluid(), drained);
    }

    /** Upstream {@code TankManager.fill}: each tank in turn takes what it can. */
    @Override
    public long fillFluid(FluidAmount fluid) {
        return fill(fluid.fluid(), fluid.amount(), false);
    }

    private long fill(Fluid fluid, long amount, boolean simulate) {
        long left = amount;
        for (Tank tank : tanks) {
            if (left <= 0) break;
            left -= tank.fill(fluid, (int) Math.min(Integer.MAX_VALUE, left), simulate);
        }
        if (left != amount && !simulate) tanksChanged();
        return amount - left;
    }

    void tanksChanged() {
        setChanged();
        SnapshotBuilder<?> builder = getBuilder();
        if (builder != null) builder.resourcesChanged();
    }

    @Nullable
    @Override
    public EnergyPort energyPort(@Nullable Direction side) {
        return energyPort;
    }

    @Nullable
    @Override
    public FluidPort fluidPort(@Nullable Direction side) {
        return fluidPort;
    }

    /** Upstream's tank manager on every face: fills each tank in turn and drains the first fluid it finds from all tanks holding it. */
    private final class BuilderTanks implements FluidPort {
        @Override
        public long fill(Fluid fluid, long amount, boolean simulate) {
            return BuilderBlockEntity.this.fill(fluid, amount, simulate);
        }

        @Override
        public FluidAmount drain(@Nullable Fluid filter, long amount, boolean simulate) {
            Fluid draining = filter;
            if (draining == null) {
                for (Tank tank : tanks) {
                    if (!tank.isEmpty()) {
                        draining = tank.fluid();
                        break;
                    }
                }
            }
            if (draining == null) return FluidAmount.EMPTY;
            return drainFluid(new FluidAmount(draining, amount), simulate);
        }

        @Override
        public List<FluidAmount> contents() {
            return tanks.stream().map(Tank::contents).toList();
        }

        @Override
        public long capacity(int index) {
            return TANK_CAPACITY;
        }

        @Override
        public Object snapshot() {
            return tanks.stream().mapToLong(Tank::snapshotValue).toArray();
        }

        @Override
        public void restore(Object snapshot) {
            long[] values = (long[]) snapshot;
            for (int i = 0; i < TANKS; i++) tanks.get(i).restoreValue(values[i]);
        }

        @Override
        public void onCommitted() {
            tanksChanged();
        }
    }

    // Container

    @Override public int getContainerSize() { return items.size(); }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) slotChanged(slot);
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) slotChanged(slot);
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        ItemStack before = items.get(slot);
        items.set(slot, stack);
        if (slot != SLOT_SNAPSHOT || !ItemStack.matches(before, stack)) slotChanged(slot);
    }

    private void slotChanged(int slot) {
        if (slot == SLOT_SNAPSHOT) onSnapshotSlotChanged();
        else resourcesChanged();
    }

    private void resourcesChanged() {
        setChanged();
        SnapshotBuilder<?> builder = getBuilder();
        if (builder != null) builder.resourcesChanged();
    }

    /** Upstream's snapshot slot takes recorded templates and blueprints only; resources take anything. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot != SLOT_SNAPSHOT || stack.getItem() instanceof SnapshotItem snapshotItem && snapshotItem.used;
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
    }

    @Override
    public void clearContent() {
        items.clear();
        onSnapshotSlotChanged();
        resourcesChanged();
    }

    @Override public int[] getSlotsForFace(Direction side) { return SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return true; }

    // Menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraft.builder");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BuilderMenu(id, inventory, this);
    }

    // Persistence

    /**
     * Upstream did not save its snapshot, and reloading the slot sent the builder back to the first step of its
     * path; this port reloads the snapshot from its item and keeps the step.
     */
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("battery", battery.save());
        if (owner != null) tag.put("owner", NbtUtils.writeGameProfile(new CompoundTag(), owner));
        ListTag tanksTag = new ListTag();
        for (Tank tank : tanks) tanksTag.add(tank.save());
        tag.put("tanks", tanksTag);
        if (path != null) tag.put("path", writePositions(path));
        tag.put("basePoses", writePositions(basePoses));
        tag.putInt("currentBasePosIndex", currentBasePosIndex);
        tag.putBoolean("canExcavate", canExcavate);
        if (rotation != null) tag.putString("rotation", rotation.name());
        SnapshotBuilder<?> builder = getBuilder();
        if (builder != null) tag.put("builder", builder.save());
        ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        battery.load(tag.getCompound("battery"));
        owner = tag.contains("owner", Tag.TAG_COMPOUND) ? NbtUtils.readGameProfile(tag.getCompound("owner")) : null;
        ListTag tanksTag = tag.getList("tanks", Tag.TAG_COMPOUND);
        for (int i = 0; i < TANKS; i++) {
            if (i < tanksTag.size()) tanks.get(i).load(tanksTag.getCompound(i));
            else tanks.get(i).clear();
        }
        path = tag.contains("path") ? readPositions(tag.getList("path", Tag.TAG_COMPOUND)) : null;
        basePoses = new ArrayList<>(readPositions(tag.getList("basePoses", Tag.TAG_COMPOUND)));
        currentBasePosIndex = tag.getInt("currentBasePosIndex");
        canExcavate = !tag.contains("canExcavate") || tag.getBoolean("canExcavate");
        rotation = readRotation(tag.getString("rotation"));
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        snapshot = loadSnapshot(items.get(SLOT_SNAPSHOT));
        updateSnapshot(false);
        SnapshotBuilder<?> builder = getBuilder();
        if (builder != null && tag.contains("builder")) builder.load(tag.getCompound("builder"));
    }

    @Nullable
    private static Rotation readRotation(String name) {
        for (Rotation value : Rotation.values()) {
            if (value.name().equals(name)) return value;
        }
        return null;
    }

    private static ListTag writePositions(List<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) list.add(NbtUtils.writeBlockPos(pos));
        return list;
    }

    private static List<BlockPos> readPositions(ListTag list) {
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) positions.add(NbtUtils.readBlockPos(list.getCompound(i)));
        return List.copyOf(positions);
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.put("box", currentBox.save());
        if (path != null) tag.put("path", new LongArrayTag(path.stream().mapToLong(BlockPos::asLong).toArray()));
        tag.putString("snapshotType", snapshotType == null ? "" : snapshotType.name());
        tag.putBoolean("canExcavate", canExcavate);
        SnapshotBuilder<?> builder = getBuilder();
        if (builder != null) builder.writeClient(tag);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        currentBox = new Box();
        currentBox.load(tag.getCompound("box"));
        path = tag.contains("path") ? Arrays.stream(tag.getLongArray("path")).mapToObj(BlockPos::of).toList() : null;
        snapshotType = null;
        for (EnumSnapshotType type : EnumSnapshotType.values()) {
            if (type.name().equals(tag.getString("snapshotType"))) snapshotType = type;
        }
        canExcavate = tag.getBoolean("canExcavate");
        SnapshotBuilder<?> builder = getBuilder();
        if (builder != null) builder.readClient(tag);
    }
}
