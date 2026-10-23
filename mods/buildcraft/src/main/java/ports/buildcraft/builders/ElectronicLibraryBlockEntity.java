package ports.buildcraft.builders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.snapshot.InvalidSnapshotException;
import ports.buildcraft.builders.snapshot.Snapshot;
import ports.buildcraft.builders.snapshot.SnapshotPackets;
import ports.buildcraft.builders.snapshot.SnapshotStorage;
import ports.buildcraft.lib.DeltaInt;
import ports.buildcraft.lib.SyncedBlockEntity;
import ports.buildcraft.platform.Platform;

/**
 * Upstream {@code TileElectronicLibrary}: copies recordings between the server and the snapshots kept on each
 * player's own computer. A recorded template or blueprint put in {@code downIn} is sent, after 50 ticks, to the
 * clients with the GUI open, which add it to their local library, and comes out of {@code downOut}. With a
 * local snapshot selected and any template or blueprint in {@code upIn}, after 50 ticks the clients are asked
 * for it; the one that has it uploads it in parts, and the server stores it and puts a recording of it in
 * {@code upOut}.
 */
public class ElectronicLibraryBlockEntity extends SyncedBlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_DOWN_IN = 0;
    public static final int SLOT_DOWN_OUT = 1;
    public static final int SLOT_UP_IN = 2;
    public static final int SLOT_UP_OUT = 3;
    public static final int TRANSFER_TICKS = 50;
    /** Upstream's upload part size. */
    public static final int UPLOAD_PART_BYTES = 4 * 1024;
    /** The most an upload may send in all, well past what any blueprint compresses to. */
    private static final int MAX_UPLOAD_BYTES = 32 * 1024 * 1024;
    /** The most blocks an upload may span: a blueprint's data alone takes 4 bytes a block of the 64 MiB the server reads. */
    private static final long MAX_UPLOAD_VOLUME = 1 << 24;
    public static final ResourceLocation DOWN_CHANNEL = BuildCraft.id("library_down");
    public static final ResourceLocation UP_REQUEST_CHANNEL = BuildCraft.id("library_up_request");
    public static final ResourceLocation UP_CHANNEL = BuildCraft.id("library_up");
    public static final ResourceLocation SELECT_CHANNEL = BuildCraft.id("library_select");
    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");
    private static final int[] SLOTS = {SLOT_DOWN_IN, SLOT_DOWN_OUT, SLOT_UP_IN, SLOT_UP_OUT};

    private final NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
    @Nullable private Snapshot.Key selected;
    private int progressDown = -1;
    private int progressUp = -1;
    public final DeltaInt deltaProgressDown = new DeltaInt();
    public final DeltaInt deltaProgressUp = new DeltaInt();
    /** Each player's upload in progress; a part of another snapshot starts it over. */
    private final Map<UUID, Upload> uploads = new HashMap<>();

    private record Upload(Snapshot.Key key, ByteArrayOutputStream bytes) {}

    public ElectronicLibraryBlockEntity(BlockPos pos, BlockState state) {
        super(BCBuilders.ELECTRONIC_LIBRARY_TYPE.get(), pos, state);
    }

    void serverTick() {
        long now = level.getGameTime();
        deltaProgressDown.tick(now);
        deltaProgressUp.tick(now);

        if (!items.get(SLOT_DOWN_IN).isEmpty() && items.get(SLOT_DOWN_OUT).isEmpty()) {
            if (progressDown == -1) {
                progressDown = 0;
                startProgress(deltaProgressDown, now);
            }
            if (progressDown >= TRANSFER_TICKS) {
                ItemStack recorded = items.get(SLOT_DOWN_IN);
                for (ServerPlayer viewer : viewers()) Platform.get().sendToPlayer(viewer, DOWN_CHANNEL, buffer -> writeDown(buffer, recorded));
                items.set(SLOT_DOWN_OUT, recorded);
                items.set(SLOT_DOWN_IN, ItemStack.EMPTY);
                progressDown = -1;
                setChanged();
            } else {
                progressDown++;
            }
        } else if (progressDown != -1) {
            progressDown = -1;
            stopProgress(deltaProgressDown);
        }

        if (selected != null && !items.get(SLOT_UP_IN).isEmpty() && items.get(SLOT_UP_OUT).isEmpty()) {
            if (progressUp == -1) {
                progressUp = 0;
                startProgress(deltaProgressUp, now);
            }
            if (progressUp >= TRANSFER_TICKS) {
                for (ServerPlayer viewer : viewers()) Platform.get().sendToPlayer(viewer, UP_REQUEST_CHANNEL, buffer -> buffer.writeBlockPos(worldPosition));
                progressUp = -1;
            } else {
                progressUp++;
            }
        } else if (progressUp != -1) {
            progressUp = -1;
            stopProgress(deltaProgressUp);
        }
        flushSync();
    }

    void clientTick() {
        long now = level.getGameTime();
        deltaProgressDown.tick(now);
        deltaProgressUp.tick(now);
    }

    private void startProgress(DeltaInt delta, long now) {
        delta.addDelta(now, 0, TRANSFER_TICKS, 1);
        delta.addDelta(now, TRANSFER_TICKS, TRANSFER_TICKS + 5, -1);
        queueSync();
    }

    private void stopProgress(DeltaInt delta) {
        delta.setValue(0);
        queueSync();
    }

    /** Upstream's {@code sendNetworkGuiUpdate}: the players with this library's GUI open. */
    private List<ServerPlayer> viewers() {
        return ((ServerLevel) level).players().stream()
                .filter(player -> player.containerMenu instanceof ElectronicLibraryMenu menu && menu.library() == this)
                .toList();
    }

    @Nullable
    public Snapshot.Key getSelected() {
        return selected;
    }

    void setSelected(@Nullable Snapshot.Key selected) {
        this.selected = selected;
        queueSync();
    }

    // Networking

    /** Upstream {@code NET_DOWN}: the recording's snapshot, keyed with its header so the local library can list it by name. */
    public static void writeDown(FriendlyByteBuf buffer, ItemStack recorded) {
        Snapshot.Header header = SnapshotItem.getHeader(recorded);
        Snapshot snapshot = header == null ? null : SnapshotStorage.get(SnapshotStorage.Side.SERVER).getSnapshot(header.key);
        buffer.writeBoolean(snapshot != null);
        if (snapshot == null) return;
        Snapshot copy = snapshot.copy();
        copy.key = new Snapshot.Key(copy.key, header);
        SnapshotPackets.write(buffer, copy);
    }

    /** Client side: adds a sent snapshot to the local library. */
    public static void receiveDown(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) return;
        try {
            Snapshot snapshot = SnapshotPackets.read(buffer);
            snapshot.computeKey();
            SnapshotStorage.get(SnapshotStorage.Side.CLIENT).addSnapshot(snapshot);
        } catch (IOException | InvalidSnapshotException e) {
            LOGGER.error("Failed to read a snapshot from the electronic library", e);
        }
    }

    /**
     * Client side, upstream {@code NET_UP}: if the local library has {@code selected}, hands {@code sender} one
     * payload for each part of it.
     */
    public static void upload(BlockPos pos, Snapshot.Key selected, int partBytes, Consumer<Consumer<FriendlyByteBuf>> sender) {
        Snapshot snapshot = SnapshotStorage.get(SnapshotStorage.Side.CLIENT).getSnapshot(selected);
        if (snapshot == null) return;
        byte[] bytes = SnapshotPackets.compress(snapshot);
        int start = 0;
        do {
            int end = Math.min(bytes.length, start + partBytes);
            byte[] part = Arrays.copyOfRange(bytes, start, end);
            boolean last = end == bytes.length;
            sender.accept(buffer -> {
                buffer.writeBlockPos(pos);
                buffer.writeNbt(selected.serializeNBT());
                buffer.writeBoolean(last);
                buffer.writeByteArray(part);
            });
            start = end;
        } while (start < bytes.length);
    }

    /** Server side: an upload part from a player with this library's GUI open. */
    public static void receiveUp(ServerPlayer player, FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        CompoundTag keyTag = buffer.readNbt();
        boolean last = buffer.readBoolean();
        byte[] part = buffer.readByteArray(UPLOAD_PART_BYTES);
        if (keyTag == null || !(player.containerMenu instanceof ElectronicLibraryMenu menu) || menu.library() == null
                || !menu.library().getBlockPos().equals(pos) || !menu.stillValid(player)) {
            return;
        }
        menu.library().receiveUpPart(player.getUUID(), new Snapshot.Key(keyTag), last, part);
    }

    /**
     * Upstream stored any upload and filled {@code upOut} with it even when {@code upIn} was empty, which made
     * recordings from nothing; here an upload only counts while {@code upIn} holds an item to replace.
     */
    private void receiveUpPart(UUID player, Snapshot.Key key, boolean last, byte[] part) {
        Upload upload = uploads.get(player);
        if (upload == null || !upload.key().equals(key)) {
            upload = new Upload(key, new ByteArrayOutputStream());
            uploads.put(player, upload);
        }
        upload.bytes().writeBytes(part);
        if (upload.bytes().size() > MAX_UPLOAD_BYTES) {
            uploads.remove(player);
            return;
        }
        if (!last) return;
        uploads.remove(player);
        try {
            Snapshot uploaded = SnapshotPackets.decompress(upload.bytes().toByteArray());
            if (Snapshot.getVolume(uploaded.size) > MAX_UPLOAD_VOLUME) {
                LOGGER.warn("Refused a snapshot of {} blocks uploaded to the electronic library", uploaded.size.toShortString());
                return;
            }
            Snapshot.Header header = uploaded.key.header;
            if (header == null || items.get(SLOT_UP_IN).isEmpty() || !items.get(SLOT_UP_OUT).isEmpty()) return;
            Snapshot snapshot = uploaded.copy();
            snapshot.key = new Snapshot.Key(snapshot.key, (Snapshot.Header) null);
            snapshot.computeKey();
            // Reading canonicalized the upload, and the client's header names whatever hash it liked: the
            // recording must name what is stored.
            if (!Arrays.equals(header.key.hash, snapshot.key.hash)) header = new Snapshot.Header(snapshot.key, header.owner, header.created, header.name);
            SnapshotStorage.get(SnapshotStorage.Side.SERVER).addSnapshot(snapshot);
            items.set(SLOT_UP_OUT, SnapshotItem.getUsed(snapshot.getType(), header));
            items.set(SLOT_UP_IN, ItemStack.EMPTY);
            setChanged();
        } catch (IOException | InvalidSnapshotException | RuntimeException e) {
            // NbtAccounter reports a tag past its limit with a bare RuntimeException.
            LOGGER.error("Failed to read a snapshot uploaded to the electronic library", e);
        }
    }

    /** Upstream {@code ContainerElectronicLibrary}'s selection message; the GUI sends it when a row is clicked. */
    public static void receiveSelect(ServerPlayer player, FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        CompoundTag keyTag = buffer.readBoolean() ? buffer.readNbt() : null;
        if (player.containerMenu instanceof ElectronicLibraryMenu menu && menu.library() != null
                && menu.library().getBlockPos().equals(pos) && menu.stillValid(player)) {
            menu.library().setSelected(keyTag == null ? null : new Snapshot.Key(keyTag));
        }
    }

    public static void writeSelect(FriendlyByteBuf buffer, BlockPos pos, @Nullable Snapshot.Key key) {
        buffer.writeBlockPos(pos);
        buffer.writeBoolean(key != null);
        if (key != null) buffer.writeNbt(key.serializeNBT());
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
        if (!removed.isEmpty()) onSlotChange(slot);
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        onSlotChange(slot);
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        onSlotChange(slot);
    }

    /** Upstream {@code onSlotChange}: changing an input part way through a copy starts it again. */
    private void onSlotChange(int slot) {
        if (slot == SLOT_DOWN_IN && progressDown > 0) {
            progressDown = -1;
            stopProgress(deltaProgressDown);
        }
        if (slot == SLOT_UP_IN && progressUp > 0) {
            progressUp = -1;
            stopProgress(deltaProgressUp);
        }
        setChanged();
    }

    /** Upstream: {@code downIn} takes recorded templates and blueprints, {@code upIn} any; the outputs take nothing. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return accepts(slot, stack);
    }

    static boolean accepts(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_DOWN_IN -> stack.getItem() instanceof SnapshotItem item && item.used;
            case SLOT_UP_IN -> stack.getItem() instanceof SnapshotItem;
            default -> false;
        };
    }

    @Override
    public int getMaxStackSize() {
        return 1;
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
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return slot == SLOT_DOWN_OUT || slot == SLOT_UP_OUT; }

    // Menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraft.electronic_library");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ElectronicLibraryMenu(id, inventory, this);
    }

    // Persistence

    /** Upstream saved only the slots; the selection and a copy in progress start over after a reload. */
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        if (selected != null) tag.put("selected", selected.serializeNBT());
        tag.put("progressDown", deltaProgressDown.save());
        tag.put("progressUp", deltaProgressUp.save());
    }

    @Override
    protected void readClient(CompoundTag tag) {
        selected = tag.contains("selected") ? new Snapshot.Key(tag.getCompound("selected")) : null;
        deltaProgressDown.load(tag.getCompound("progressDown"));
        deltaProgressUp.load(tag.getCompound("progressUp"));
    }
}
