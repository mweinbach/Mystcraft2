package ports.buildcraft.builders;

import java.util.Date;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ports.buildcraft.builders.snapshot.Blueprint;
import ports.buildcraft.builders.snapshot.EnumSnapshotType;
import ports.buildcraft.builders.snapshot.InvalidSnapshotException;
import ports.buildcraft.builders.snapshot.SchematicBlock;
import ports.buildcraft.builders.snapshot.Snapshot;
import ports.buildcraft.builders.snapshot.SnapshotStorage;

/**
 * Upstream {@code TileReplacer}: with a recorded blueprint and two recorded single schematics in, it stores a
 * copy of the blueprint with every block matching the first schematic replaced by the second, puts a recording
 * of the copy in place of the blueprint, keeping its name, and uses up both schematics. Its slots are closed to
 * automation, as upstream.
 */
public class ReplacerBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_SNAPSHOT = 0;
    public static final int SLOT_FROM = 1;
    public static final int SLOT_TO = 2;
    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");
    private static final int[] NO_SLOTS = {};

    private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private UUID owner = new UUID(0, 0);

    public ReplacerBlockEntity(BlockPos pos, BlockState state) {
        super(BCBuilders.REPLACER_TYPE.get(), pos, state);
    }

    void onPlaced(@Nullable LivingEntity placer) {
        if (placer instanceof Player player) {
            owner = player.getUUID();
            setChanged();
        }
    }

    /**
     * Upstream labelled the new recording with the old blueprint's key, so the result still built the unchanged
     * blueprint; here it names the replaced copy.
     */
    void serverTick() {
        ItemStack recorded = items.get(SLOT_SNAPSHOT);
        if (recorded.isEmpty() || items.get(SLOT_FROM).isEmpty() || items.get(SLOT_TO).isEmpty()) return;
        Snapshot.Header header = SnapshotItem.getHeader(recorded);
        if (header == null) return;
        SnapshotStorage storage = SnapshotStorage.get(SnapshotStorage.Side.SERVER);
        if (!(storage.getSnapshot(header.key) instanceof Blueprint blueprint)) return;
        try {
            SchematicBlock from = SchematicSingleItem.getSchematic(items.get(SLOT_FROM));
            SchematicBlock to = SchematicSingleItem.getSchematic(items.get(SLOT_TO));
            Blueprint replaced = blueprint.copy();
            replaced.replace(from, to);
            replaced.computeKey();
            storage.addSnapshot(replaced);
            items.set(SLOT_SNAPSHOT, SnapshotItem.getUsed(EnumSnapshotType.BLUEPRINT, new Snapshot.Header(replaced.key, owner, new Date(), header.name)));
            items.set(SLOT_FROM, ItemStack.EMPTY);
            items.set(SLOT_TO, ItemStack.EMPTY);
            setChanged();
        } catch (InvalidSnapshotException e) {
            LOGGER.warn("The replacer at {} holds an invalid schematic", worldPosition, e);
        }
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

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return accepts(slot, stack);
    }

    /** Upstream: a recorded blueprint, then a recorded single schematic to replace and one to replace it with. */
    static boolean accepts(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_SNAPSHOT -> stack.is(BCBuilders.BLUEPRINT_USED.get());
            case SLOT_FROM, SLOT_TO -> stack.is(BCBuilders.SCHEMATIC_SINGLE_USED.get());
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

    @Override public int[] getSlotsForFace(Direction side) { return NO_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }

    // Menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraft.replacer");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ReplacerMenu(id, inventory, this);
    }

    // Persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putUUID("owner", owner);
        ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : new UUID(0, 0);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
    }
}
