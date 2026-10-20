package ports.buildcraft.builders;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.builders.snapshot.Blueprint;
import ports.buildcraft.builders.snapshot.EnumSnapshotType;
import ports.buildcraft.builders.snapshot.SchematicBlock;
import ports.buildcraft.builders.snapshot.SchematicBlockContext;
import ports.buildcraft.builders.snapshot.SchematicBlockManager;
import ports.buildcraft.builders.snapshot.SchematicEntity;
import ports.buildcraft.builders.snapshot.SchematicEntityContext;
import ports.buildcraft.builders.snapshot.SchematicEntityManager;
import ports.buildcraft.builders.snapshot.Snapshot;
import ports.buildcraft.builders.snapshot.SnapshotStorage;
import ports.buildcraft.builders.snapshot.Template;
import ports.buildcraft.core.marker.volume.Lock;
import ports.buildcraft.core.marker.volume.VolumeBox;
import ports.buildcraft.core.marker.volume.VolumeBoxes;
import ports.buildcraft.lib.AreaProvider;
import ports.buildcraft.lib.Box;
import ports.buildcraft.lib.BoxIterator;
import ports.buildcraft.lib.DeltaInt;
import ports.buildcraft.lib.SyncedBlockEntity;

/**
 * Upstream {@code TileArchitectTable}: takes the volume box or marked area behind it, and records it onto the
 * template or blueprint put in, a few hundred blocks a tick. The recording goes into the snapshot storage and
 * the item comes out carrying its header.
 */
public class ArchitectTableBlockEntity extends SyncedBlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_IN = 0;
    public static final int SLOT_OUT = 1;
    public static final String DEFAULT_NAME = "<unnamed>";
    private static final int[] SLOTS = {SLOT_IN, SLOT_OUT};

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private EnumSnapshotType snapshotType = EnumSnapshotType.BLUEPRINT;
    public final Box box = new Box();
    public boolean markerBox;
    @Nullable private BitSet templateScannedBlocks;
    private final List<SchematicBlock> blueprintScannedPalette = new ArrayList<>();
    @Nullable private int[] blueprintScannedData;
    private final List<SchematicEntity> blueprintScannedEntities = new ArrayList<>();
    @Nullable private BoxIterator boxIterator;
    private boolean isValid;
    private boolean scanning;
    public String name = DEFAULT_NAME;
    private UUID owner = new UUID(0, 0);
    public final DeltaInt deltaProgress = new DeltaInt();
    private final List<BlockPos> clientScans = new ArrayList<>();
    private boolean scansSent;

    public ArchitectTableBlockEntity(BlockPos pos, BlockState state) {
        super(BCBuilders.ARCHITECT_TABLE_TYPE.get(), pos, state);
    }

    /**
     * Upstream {@code onPlacedBy}: a volume box behind the table is copied and locked against removal and
     * resizing; otherwise a marked area behind it is copied and its marks removed; otherwise the table is
     * left invalid and its front goes dark.
     */
    void onPlaced(@Nullable LivingEntity placer) {
        if (placer instanceof Player player) owner = player.getUUID();
        BlockState state = getBlockState();
        BlockPos offsetPos = worldPosition.relative(state.getValue(ArchitectTableBlock.FACING).getOpposite());
        VolumeBoxes volumeBoxes = VolumeBoxes.get((ServerLevel) level);
        VolumeBox volumeBox = volumeBoxes.getVolumeBoxAt(offsetPos);
        if (volumeBox != null) {
            box.set(volumeBox.box.min(), volumeBox.box.max());
            isValid = true;
            volumeBox.locks.add(new Lock(new Lock.CauseBlock(worldPosition, state.getBlock()),
                    new Lock.Remove(), new Lock.Resize(), new Lock.UsedByMachine(Lock.MachineUse.STRIPES_READ)));
            volumeBoxes.markDirty();
        } else if (level.getBlockEntity(offsetPos) instanceof AreaProvider provider) {
            box.set(provider.min(), provider.max());
            markerBox = true;
            isValid = true;
            provider.removeFromWorld();
        } else {
            isValid = false;
            level.setBlockAndUpdate(worldPosition, state.setValue(ArchitectTableBlock.VALID, false));
        }
        setChanged();
        queueSync();
    }

    void clientTick() {
        long now = level.getGameTime();
        deltaProgress.tick(now);
        if (box.isInitialized()) ClientArchitectTables.putBox(box.boundingBox(), now);
    }

    void serverTick() {
        long now = level.getGameTime();
        deltaProgress.tick(now);
        if (scansSent) {
            clientScans.clear();
            scansSent = false;
        }

        ItemStack in = items.get(SLOT_IN);
        if (in.getItem() instanceof SnapshotItem snapshotItem && items.get(SLOT_OUT).isEmpty() && isValid) {
            if (!scanning) {
                if (snapshotItem.snapshotType != snapshotType) clearScan();
                snapshotType = snapshotItem.snapshotType;
                BlockPos size = box.size();
                int ticks = size.getX() * size.getY() * size.getZ() / snapshotType.maxPerTick;
                deltaProgress.addDelta(now, 0, ticks, 1);
                deltaProgress.addDelta(now, ticks, ticks + 10, -1);
                scanning = true;
                setChanged();
                queueSync();
            }
        } else if (scanning) {
            scanning = false;
            setChanged();
        }

        if (scanning) {
            scanMultipleBlocks();
            if (!scanning) {
                if (snapshotType == EnumSnapshotType.BLUEPRINT) scanEntities();
                finishScanning();
            }
        }
        if (!clientScans.isEmpty()) {
            scansSent = true;
            queueSync();
        }
        flushSync();
    }

    private void scanMultipleBlocks() {
        for (int i = snapshotType.maxPerTick; i > 0 && scanning; i--) {
            scanSingleBlock();
        }
    }

    private void scanSingleBlock() {
        BlockPos size = box.size();
        if (templateScannedBlocks == null || blueprintScannedData == null || boxIterator == null) {
            boxIterator = new BoxIterator(box.min(), box.max(), Direction.EAST, Direction.SOUTH, Direction.UP, true);
            templateScannedBlocks = new BitSet(Snapshot.getDataSize(size));
            blueprintScannedData = new int[Snapshot.getDataSize(size)];
            blueprintScannedPalette.clear();
        }

        BlockPos worldScanPos = Objects.requireNonNull(boxIterator.getCurrent());
        BlockPos schematicPos = worldScanPos.subtract(box.min());
        if (snapshotType == EnumSnapshotType.TEMPLATE) {
            templateScannedBlocks.set(Snapshot.posToIndex(size, schematicPos), !level.getBlockState(worldScanPos).isAir());
        }
        if (snapshotType == EnumSnapshotType.BLUEPRINT) {
            SchematicBlock schematicBlock = SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(level, box.min(), worldScanPos));
            int index = blueprintScannedPalette.indexOf(schematicBlock);
            if (index == -1) {
                index = blueprintScannedPalette.size();
                blueprintScannedPalette.add(schematicBlock);
            }
            blueprintScannedData[Snapshot.posToIndex(size, schematicPos)] = index;
        }
        clientScans.add(worldScanPos);

        boxIterator.advance();
        if (!boxIterator.hasNext()) {
            scanning = false;
            boxIterator = null;
        }
    }

    private void scanEntities() {
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box.boundingBox())) {
            SchematicEntity schematicEntity = SchematicEntityManager.getSchematicEntity(new SchematicEntityContext(level, box.min(), entity));
            if (schematicEntity != null) blueprintScannedEntities.add(schematicEntity);
        }
    }

    private void finishScanning() {
        BlockState state = getBlockState();
        if (!state.is(BCBuilders.ARCHITECT_TABLE.get())) return;
        Direction facing = state.getValue(ArchitectTableBlock.FACING);
        Snapshot snapshot = Snapshot.create(snapshotType);
        snapshot.size = box.size();
        snapshot.facing = facing;
        snapshot.offset = box.min().subtract(worldPosition.relative(facing.getOpposite()));
        if (snapshot instanceof Template template) template.data = templateScannedBlocks;
        if (snapshot instanceof Blueprint blueprint) {
            blueprint.palette.addAll(blueprintScannedPalette);
            blueprint.data = blueprintScannedData;
            blueprint.entities.addAll(blueprintScannedEntities);
        }
        snapshot.computeKey();
        SnapshotStorage.get(level).addSnapshot(snapshot);
        items.get(SLOT_IN).shrink(1);
        items.set(SLOT_OUT, SnapshotItem.getUsed(snapshotType, new Snapshot.Header(snapshot.key, owner, new Date(), name)));
        clearScan();
        setChanged();
        queueSync();
    }

    /** Upstream kept the palette between scans, so later blueprints carried unused entries; this port starts each scan afresh. */
    private void clearScan() {
        templateScannedBlocks = null;
        blueprintScannedData = null;
        blueprintScannedPalette.clear();
        blueprintScannedEntities.clear();
        boxIterator = null;
    }

    /** Upstream {@code ContainerArchitectTable}'s name message; the GUI sends it on every key press. */
    public void setName(String name) {
        this.name = name;
        setChanged();
        queueSync();
    }

    public boolean isValid() {
        return isValid;
    }

    public boolean isScanning() {
        return scanning;
    }

    public EnumSnapshotType getSnapshotType() {
        return snapshotType;
    }

    public UUID getOwner() {
        return owner;
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
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    /** Upstream's {@code in} slot takes any template or blueprint, recorded or not; {@code out} is extract only. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_IN && stack.getItem() instanceof SnapshotItem;
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override public int[] getSlotsForFace(Direction side) { return SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return slot == SLOT_OUT; }

    // Menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraft.architect_table");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ArchitectTableMenu(id, inventory, this);
    }

    // Persistence

    /** Upstream also saved the scan iterator, but always restarted the scan on load because the scanned data was not saved. */
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("box", box.save());
        tag.putBoolean("markerBox", markerBox);
        tag.putBoolean("scanning", scanning);
        tag.putString("snapshotType", snapshotType.name());
        tag.putBoolean("isValid", isValid);
        tag.putString("name", name);
        tag.putUUID("owner", owner);
        tag.put("progress", deltaProgress.save());
        ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        box.load(tag.getCompound("box"));
        markerBox = tag.getBoolean("markerBox");
        scanning = tag.getBoolean("scanning");
        snapshotType = readSnapshotType(tag.getString("snapshotType"));
        isValid = tag.getBoolean("isValid");
        name = tag.contains("name") ? tag.getString("name") : DEFAULT_NAME;
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : new UUID(0, 0);
        deltaProgress.load(tag.getCompound("progress"));
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        clearScan();
    }

    private static EnumSnapshotType readSnapshotType(String name) {
        for (EnumSnapshotType type : EnumSnapshotType.values()) {
            if (type.name().equals(name)) return type;
        }
        return EnumSnapshotType.BLUEPRINT;
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.put("box", box.save());
        tag.putBoolean("markerBox", markerBox);
        tag.putString("name", name);
        tag.put("progress", deltaProgress.save());
        if (!clientScans.isEmpty()) tag.put("scans", new LongArrayTag(clientScans.stream().mapToLong(BlockPos::asLong).toArray()));
    }

    /** Upstream sent each scanned block as its own message; here each tick's blocks ride on the table's update. */
    @Override
    protected void readClient(CompoundTag tag) {
        box.load(tag.getCompound("box"));
        markerBox = tag.getBoolean("markerBox");
        name = tag.getString("name");
        deltaProgress.load(tag.getCompound("progress"));
        long now = level.getGameTime();
        for (long scan : tag.getLongArray("scans")) ClientArchitectTables.putScannedBlock(BlockPos.of(scan), now);
    }
}
