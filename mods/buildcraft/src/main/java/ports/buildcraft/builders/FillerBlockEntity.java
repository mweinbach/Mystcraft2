package ports.buildcraft.builders;

import java.util.Arrays;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.builders.filler.FilledTemplate;
import ports.buildcraft.builders.filler.FillerParameter;
import ports.buildcraft.builders.filler.FillerPatterns;
import ports.buildcraft.builders.filler.Pattern;
import ports.buildcraft.builders.filler.PatternStatement;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.builders.snapshot.BuildingInfo;
import ports.buildcraft.builders.snapshot.TemplateBuilder;
import ports.buildcraft.core.marker.volume.Lock;
import ports.buildcraft.core.marker.volume.VolumeBox;
import ports.buildcraft.core.marker.volume.VolumeBoxes;
import ports.buildcraft.lib.AreaProvider;
import ports.buildcraft.lib.Box;
import ports.buildcraft.lib.Controllable;
import ports.buildcraft.lib.InventoryUtil;
import ports.buildcraft.lib.MjBattery;
import ports.buildcraft.lib.Power;
import ports.buildcraft.lib.SyncedBlockEntity;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.PortHolder;

/**
 * Upstream {@code TileFiller}: takes the volume box or marked area behind it, fills that box with the
 * selected pattern from block items in its 27 resource slots, and clears blocks the pattern leaves
 * empty. A gate can set that pattern and turn the filler on, off or into a loop. Resources can be
 * inserted from any side but not pulled out.
 */
public class FillerBlockEntity extends SyncedBlockEntity implements PortHolder, WorldlyContainer, MenuProvider, TemplateBuilder.Host, Controllable {
    public static final int RESOURCE_SLOTS = 27;
    private static final int[] SLOTS = IntStream.range(0, RESOURCE_SLOTS).toArray();
    public static final int BUTTON_EXCAVATE = 0;
    public static final int BUTTON_INVERT = 2;
    public static final int BUTTON_PATTERN = 1000;
    public static final int BUTTON_PARAMETER = 2000;

    private final NonNullList<ItemStack> resources = NonNullList.withSize(RESOURCE_SLOTS, ItemStack.EMPTY);
    private final MjBattery battery = new MjBattery(16_000 * Power.MJ);
    private final EnergyPort energyPort = battery.receiver(false, this::setChanged);
    private boolean canExcavate = true;
    private boolean inverted;
    private boolean finished;
    private byte lockedTicks;
    private Controllable.Mode mode = Controllable.Mode.ON;
    public final Box box = new Box();
    public boolean markerBox = true;
    public final PatternStatement patternStatement = new PatternStatement();
    @Nullable private BuildingInfo buildingInfo;
    @Nullable private FilledTemplate template;
    public final TemplateBuilder builder = new TemplateBuilder(this);
    private boolean syncedTasks;
    private int syncedLeftToBreak;
    private int syncedLeftToPlace;

    public FillerBlockEntity(BlockPos pos, BlockState state) {
        super(BCBuilders.FILLER_TYPE.get(), pos, state);
    }

    /**
     * Upstream {@code onPlacedBy}: a volume box behind the filler is copied and locked against removal and
     * resizing; otherwise a marked area behind it is copied and its marks removed.
     */
    void onPlaced() {
        Direction facing = getBlockState().getValue(FillerBlock.FACING);
        BlockPos offsetPos = worldPosition.relative(facing.getOpposite());
        VolumeBoxes volumeBoxes = VolumeBoxes.get((ServerLevel) level);
        VolumeBox volumeBox = volumeBoxes.getVolumeBoxAt(offsetPos);
        if (volumeBox != null) {
            box.set(volumeBox.box.min(), volumeBox.box.max());
            volumeBox.locks.add(new Lock(new Lock.CauseBlock(worldPosition, getBlockState().getBlock()),
                    new Lock.Remove(), new Lock.Resize(), new Lock.UsedByMachine(Lock.MachineUse.STRIPES_WRITE)));
            volumeBoxes.markDirty();
            markerBox = false;
        } else if (level.getBlockEntity(offsetPos) instanceof AreaProvider provider) {
            box.set(provider.min(), provider.max());
            provider.removeFromWorld();
        }
        updateBuildingInfo();
        setChanged();
        queueSync();
    }

    /** Upstream {@code TileBC_Neptune.onRemove}: in-flight blocks go back to the resources, which drop. */
    void onRemoved() {
        builder.cancel();
        Containers.dropContents(level, worldPosition, resources);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        builder.stopListening();
    }

    private void updateBuildingInfo() {
        builder.cancel();
        buildingInfo = null;
        template = null;
        if (hasBox()) {
            FilledTemplate filled = patternStatement.get().createTemplate(box.size(), patternStatement.parameters());
            if (filled != null) {
                if (inverted) filled.invert();
                template = filled;
                buildingInfo = new BuildingInfo(BlockPos.ZERO, box.min(), box.size(), Rotation.NONE);
            }
        }
        builder.updateSnapshot();
    }

    private void onStatementChange() {
        finished = false;
        updateBuildingInfo();
        setChanged();
        queueSync();
    }

    void clientTick() {
        if (hasBox()) builder.clientTick();
    }

    void serverTick() {
        builder.startListening();
        boolean wasLocked = isLocked();
        if (lockedTicks > 0) lockedTicks--;
        if (wasLocked && !isLocked()) queueSync();
        if (mode != Controllable.Mode.OFF && getBuilder() != null) {
            builder.tick();
            setChanged();
        }
        boolean hasTasks = builder.hasTasks();
        if (hasTasks || syncedTasks || builder.leftToBreak != syncedLeftToBreak || builder.leftToPlace != syncedLeftToPlace) {
            syncedTasks = hasTasks;
            syncedLeftToBreak = builder.leftToBreak;
            syncedLeftToPlace = builder.leftToPlace;
            queueSync();
        }
        flushSync();
    }

    /** The GUI's buttons, as upstream's container messages; ids are the {@code BUTTON_*} constants plus an offset. */
    public boolean handleButton(int id) {
        if (id == BUTTON_EXCAVATE || id == BUTTON_EXCAVATE + 1) {
            canExcavate = id == BUTTON_EXCAVATE + 1;
            setChanged();
            queueSync();
            return true;
        }
        if (id == BUTTON_INVERT || id == BUTTON_INVERT + 1) {
            inverted = id == BUTTON_INVERT + 1;
            onStatementChange();
            return true;
        }
        if (isLocked()) return false;
        if (id >= BUTTON_PATTERN && id < BUTTON_PATTERN + FillerPatterns.PATTERNS.size()) {
            patternStatement.set(FillerPatterns.PATTERNS.get(id - BUTTON_PATTERN));
            onStatementChange();
            return true;
        }
        int param = (id - BUTTON_PARAMETER) / 100;
        int value = (id - BUTTON_PARAMETER) % 100;
        if (id >= BUTTON_PARAMETER && param < PatternStatement.MAX_PARAMS) {
            FillerParameter current = patternStatement.get(param);
            if (current == null || value >= current.allValues().length) return false;
            FillerParameter chosen = current.allValues()[value];
            if (!patternStatement.canSet(param, chosen)) return false;
            patternStatement.set(param, chosen);
            onStatementChange();
            return true;
        }
        return false;
    }

    public static int patternButton(int patternIndex) {
        return BUTTON_PATTERN + patternIndex;
    }

    public static int parameterButton(int param, FillerParameter value) {
        return BUTTON_PARAMETER + param * 100 + value.ordinal();
    }

    // State

    public boolean hasBox() {
        return box.isInitialized();
    }

    public boolean isValid() {
        return hasBox() && (level == null || level.isClientSide || buildingInfo != null);
    }

    @Nullable
    public TemplateBuilder getBuilder() {
        return isValid() ? builder : null;
    }

    public boolean canExcavateSetting() {
        return canExcavate;
    }

    public boolean isInverted() {
        return inverted;
    }

    /** Upstream {@code isFinished}: a loop keeps going, so it never counts as finished. */
    public boolean isFinished() {
        return mode != Controllable.Mode.LOOP && finished;
    }

    /** Upstream locks the pattern for three ticks after a gate sets it, and for as long as the gate keeps setting it. */
    public boolean isLocked() {
        return lockedTicks > 0;
    }

    @Override
    public Controllable.Mode getControlMode() {
        return mode;
    }

    /**
     * Upstream {@code setControlMode}. Leaving off clears finished. Upstream's tick only skips work while off; the
     * check that would also stop a finished machine that is not looping is commented out upstream, and nothing in
     * upstream ever sets finished, so on and loop both keep building.
     */
    @Override
    public void setControlMode(Controllable.Mode mode) {
        if (!acceptsControlMode(mode) || this.mode == mode) return;
        if (this.mode == Controllable.Mode.OFF) finished = false;
        this.mode = mode;
        setChanged();
        queueSync();
    }

    /**
     * Upstream {@code setPattern}. The statement is replaced, the pattern stays locked for three ticks and finished
     * is cleared. Upstream did not rebuild its cached template here, so a gate's pattern never reached the builder
     * until something else refreshed it; a change rebuilds the template, and the same pattern again does not.
     */
    public void setPattern(Pattern pattern, StatementParameter[] parameters) {
        Pattern before = patternStatement.get();
        FillerParameter[] beforeParams = patternStatement.parameters();
        patternStatement.set(pattern, parameters);
        finished = false;
        boolean wasLocked = isLocked();
        lockedTicks = 3;
        if (patternStatement.get() != before || !Arrays.equals(beforeParams, patternStatement.parameters())) onStatementChange();
        else if (!wasLocked) {
            setChanged();
            queueSync();
        }
    }

    public int getCountToBreak() {
        return builder.leftToBreak;
    }

    public int getCountToPlace() {
        return builder.leftToPlace;
    }

    // TemplateBuilder.Host

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
        return isValid() ? buildingInfo : null;
    }

    @Nullable
    @Override
    public FilledTemplate template() {
        return template;
    }

    @Override
    public ItemStack extractResource(boolean simulate) {
        for (int slot = 0; slot < RESOURCE_SLOTS; slot++) {
            ItemStack stack = resources.get(slot);
            if (stack.isEmpty()) continue;
            if (simulate) {
                ItemStack one = stack.copy();
                one.setCount(1);
                return one;
            }
            ItemStack extracted = stack.split(1);
            resourcesChanged();
            return extracted;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertResource(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < RESOURCE_SLOTS && !remaining.isEmpty(); slot++) {
            ItemStack existing = resources.get(slot);
            if (existing.isEmpty()) {
                resources.set(slot, remaining);
                remaining = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameTags(existing, remaining)) {
                int moved = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(moved);
                remaining.shrink(moved);
            }
        }
        resourcesChanged();
        return remaining;
    }

    @Override
    public void dropResource(ItemStack stack) {
        InventoryUtil.drop(level, worldPosition, stack);
    }

    @Nullable
    @Override
    public EnergyPort energyPort(@Nullable Direction side) {
        return energyPort;
    }

    // Container

    @Override public int getContainerSize() { return RESOURCE_SLOTS; }

    @Override
    public boolean isEmpty() {
        return resources.stream().allMatch(ItemStack::isEmpty);
    }

    @Override public ItemStack getItem(int slot) { return resources.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(resources, slot, amount);
        if (!removed.isEmpty()) resourcesChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(resources, slot);
        resourcesChanged();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        resources.set(slot, stack);
        resourcesChanged();
    }

    private void resourcesChanged() {
        setChanged();
        builder.resourcesChanged();
    }

    /** Upstream's {@code ItemBlocks} list: every block item, which in 1.19 includes beds, doors, signs and skulls. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem() instanceof BlockItem;
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
    }

    @Override
    public void clearContent() {
        resources.clear();
        resourcesChanged();
    }

    @Override public int[] getSlotsForFace(Direction side) { return SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }

    // Menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraft.filler");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new FillerMenu(id, inventory, this);
    }

    private static Controllable.Mode readMode(CompoundTag tag) {
        String name = tag.getString("mode");
        for (Controllable.Mode value : Controllable.Mode.VALUES) {
            if (value.name().equals(name)) return value;
        }
        return Controllable.Mode.ON;
    }

    // Persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("battery", battery.save());
        tag.putBoolean("canExcavate", canExcavate);
        tag.putBoolean("inverted", inverted);
        tag.putBoolean("finished", finished);
        tag.putByte("lockedTicks", lockedTicks);
        tag.putString("mode", mode.name());
        tag.put("box", box.save());
        tag.putBoolean("markerBox", markerBox);
        tag.put("patternStatement", patternStatement.save());
        if (buildingInfo != null) tag.put("builder", builder.save());
        ContainerHelper.saveAllItems(tag, resources);
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        battery.load(tag.getCompound("battery"));
        canExcavate = !tag.contains("canExcavate") || tag.getBoolean("canExcavate");
        inverted = tag.getBoolean("inverted");
        finished = tag.getBoolean("finished");
        lockedTicks = tag.getByte("lockedTicks");
        mode = readMode(tag);
        box.load(tag.getCompound("box"));
        markerBox = !tag.contains("markerBox") || tag.getBoolean("markerBox");
        patternStatement.load(tag.getCompound("patternStatement"));
        resources.clear();
        ContainerHelper.loadAllItems(tag, resources);
        updateBuildingInfo();
        if (tag.contains("builder") && buildingInfo != null) builder.load(tag.getCompound("builder"));
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.put("box", box.save());
        tag.putBoolean("markerBox", markerBox);
        tag.put("patternStatement", patternStatement.save());
        tag.putBoolean("canExcavate", canExcavate);
        tag.putBoolean("inverted", inverted);
        tag.putBoolean("finished", finished);
        tag.putBoolean("locked", isLocked());
        tag.putString("mode", mode.name());
        builder.writeClient(tag);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        box.load(tag.getCompound("box"));
        markerBox = tag.getBoolean("markerBox");
        patternStatement.load(tag.getCompound("patternStatement"));
        canExcavate = tag.getBoolean("canExcavate");
        inverted = tag.getBoolean("inverted");
        finished = tag.getBoolean("finished");
        lockedTicks = (byte) (tag.getBoolean("locked") ? 1 : 0);
        mode = readMode(tag);
        builder.readClient(tag);
    }
}
