package ports.gridwright.transfer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The filter editor. Its nine slots are ghosts: clicking one with an item lists a copy of that item and
 * clicking it empty-handed clears it, so nothing is ever put in or taken out. Button 0 switches between
 * whitelist and blacklist and button 1 turns NBT matching on or off. The edited filter stays in the
 * player's inventory at {@code filterSlot}, which is locked while the editor is open.
 */
public class FilterMenu extends AbstractContainerMenu {

    public static final int BUTTON_BLACKLIST = 0;
    public static final int BUTTON_MATCH_NBT = 1;

    private final Inventory inventory;
    private final int filterSlot;
    private final SimpleContainer ghosts = new SimpleContainer(ItemFilter.SIZE);
    private boolean blacklist;
    private boolean matchNbt;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? (blacklist ? 1 : 0) : (matchNbt ? 1 : 0);
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) blacklist = value != 0;
            else matchNbt = value != 0;
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public FilterMenu(int containerId, Inventory inventory, int filterSlot) {
        super(TransferRegistry.FILTER_MENU.get(), containerId);
        this.inventory = inventory;
        this.filterSlot = filterSlot;
        ItemStack filter = FilterItem.held(inventory, filterSlot);
        ItemFilter current = ItemFilter.of(filter);
        blacklist = current.blacklist();
        matchNbt = current.matchNbt();
        var saved = ItemFilter.readSlots(filter);
        for (int i = 0; i < ItemFilter.SIZE; i++) ghosts.setItem(i, saved.get(i));
        for (int i = 0; i < ItemFilter.SIZE; i++) addSlot(new GhostSlot(ghosts, i, 8 + i * 18, 20));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(lockable(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) addSlot(lockable(inventory, column, 8 + column * 18, 142));
        addDataSlots(data);
    }

    public static FilterMenu client(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        return new FilterMenu(containerId, inventory, buffer.readVarInt());
    }

    public boolean blacklist() {
        return blacklist;
    }

    public boolean matchNbt() {
        return matchNbt;
    }

    public ItemStack ghost(int index) {
        return ghosts.getItem(index);
    }

    private Slot lockable(Container container, int inventorySlot, int x, int y) {
        return new Slot(container, inventorySlot, x, y) {
            // Not "index": inside a Slot that names the inherited menu index, not the inventory slot.
            @Override
            public boolean mayPickup(Player player) {
                return getContainerSlot() != filterSlot;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return getContainerSlot() != filterSlot;
            }
        };
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < ItemFilter.SIZE) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                ItemStack carried = getCarried();
                ghosts.setItem(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copy().split(1));
                save();
            }
            return;
        }
        // Number keys and the off-hand key would otherwise swap the filter being edited out of its slot.
        if (type == ClickType.SWAP && button == filterSlot) return;
        super.clicked(slotId, button, type, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_BLACKLIST) blacklist = !blacklist;
        else if (id == BUTTON_MATCH_NBT) matchNbt = !matchNbt;
        else return false;
        save();
        return true;
    }

    /** Shift-clicking an inventory item lists a copy of it in the first free ghost slot. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < ItemFilter.SIZE) return ItemStack.EMPTY;
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty() || stack.getItem() instanceof FilterItem) return ItemStack.EMPTY;
        for (int i = 0; i < ItemFilter.SIZE; i++) {
            if (ItemStack.isSameItemSameTags(ghosts.getItem(i), stack)) return ItemStack.EMPTY;
        }
        for (int i = 0; i < ItemFilter.SIZE; i++) {
            if (ghosts.getItem(i).isEmpty()) {
                ghosts.setItem(i, stack.copy().split(1));
                save();
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    private void save() {
        if (inventory.player.level.isClientSide) return;
        ItemStack filter = FilterItem.held(inventory, filterSlot);
        if (filter.isEmpty()) return;
        java.util.List<ItemStack> entries = new java.util.ArrayList<>();
        for (int i = 0; i < ItemFilter.SIZE; i++) entries.add(ghosts.getItem(i));
        ItemFilter.write(filter, entries, blacklist, matchNbt);
        inventory.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return !FilterItem.held(inventory, filterSlot).isEmpty();
    }

    private static final class GhostSlot extends Slot {
        GhostSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
