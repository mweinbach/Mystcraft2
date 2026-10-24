package ports.gridwright.item;

import ports.gridwright.registry.ModUtilities;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * A Bag of Holding's screen: a six-row chest whose slots write straight back into the bag item on every
 * change. The inventory slot holding the open bag is locked: it cannot be clicked, swapped into with a
 * number key or the offhand key, thrown, cloned or collected, and the screen closes if that slot stops
 * holding this exact stack. Bag slots refuse anything {@link BagOfHoldingItem#canHold} rejects, so bags
 * and shulker boxes never nest.
 */
public class BagMenu extends ChestMenu {

    private final int lockedSlot;
    /** The open bag on the server; {@code null} on the client. */
    @Nullable private final ItemStack bag;

    private BagMenu(int id, Inventory inventory, Container contents, int lockedSlot, @Nullable ItemStack bag) {
        super(ModUtilities.BAG_MENU.get(), id, inventory, contents, BagOfHoldingItem.ROWS);
        this.lockedSlot = lockedSlot;
        this.bag = bag;
    }

    public static BagMenu client(int id, Inventory inventory, FriendlyByteBuf data) {
        return new BagMenu(id, inventory, new Contents(null), data.readVarInt(), null);
    }

    /** The menu for the bag in {@code inventory}'s {@code slot}. */
    public static BagMenu server(int id, Inventory inventory, int slot) {
        ItemStack bag = inventory.getItem(slot);
        return new BagMenu(id, inventory, new Contents(bag), slot, bag);
    }

    public int lockedSlot() {
        return lockedSlot;
    }

    @Override
    protected Slot addSlot(Slot slot) {
        // Runs from ChestMenu's constructor, before this class's fields are set; it needs none.
        return super.addSlot(slot.container instanceof Inventory ? slot
                : new BagSlot(slot.container, slot.getContainerSlot(), slot.x, slot.y));
    }

    private boolean locked(Slot slot, Player player) {
        return slot.container == player.getInventory() && slot.getContainerSlot() == lockedSlot;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < slots.size() && locked(slots.get(slotId), player)) return;
        if (type == ClickType.SWAP && button == lockedSlot) return;
        super.clicked(slotId, button, type, player);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot.container instanceof Inventory inventory && slot.getContainerSlot() == lockedSlot
                && inventory.getItem(lockedSlot) == bag) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean stillValid(Player player) {
        return bag == null || !bag.isEmpty() && player.getInventory().getItem(lockedSlot) == bag;
    }

    /** A bag slot: refuses items that may not go in a bag. */
    private static final class BagSlot extends Slot {
        BagSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return BagOfHoldingItem.canHold(stack);
        }
    }

    /** The bag's 54 slots, loaded from and saved straight back to the bag's {@code Items} tag. */
    static final class Contents extends SimpleContainer {
        @Nullable private final ItemStack bag;
        private boolean loading;

        Contents(@Nullable ItemStack bag) {
            super(BagOfHoldingItem.SIZE);
            this.bag = bag;
            CompoundTag tag = bag == null ? null : bag.getTag();
            if (tag == null) return;
            NonNullList<ItemStack> items = NonNullList.withSize(BagOfHoldingItem.SIZE, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, items);
            loading = true;
            for (int slot = 0; slot < items.size(); slot++) setItem(slot, items.get(slot));
            loading = false;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return BagOfHoldingItem.canHold(stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (bag == null || loading) return;
            NonNullList<ItemStack> items = NonNullList.withSize(BagOfHoldingItem.SIZE, ItemStack.EMPTY);
            for (int slot = 0; slot < items.size(); slot++) items.set(slot, getItem(slot));
            ContainerHelper.saveAllItems(bag.getOrCreateTag(), items);
        }
    }
}
