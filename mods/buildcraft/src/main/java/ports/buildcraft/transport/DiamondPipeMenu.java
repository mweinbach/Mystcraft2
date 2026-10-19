package ports.buildcraft.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.lib.BCMenu;
import ports.buildcraft.transport.behaviour.DiamondItemBehaviour;

/**
 * Upstream {@code ContainerDiamondPipe}: six rows of nine phantom filter slots, one row per face in
 * {@code Direction} order. Clicking sets a single copy of the held item, clicking again with the same
 * item adds one, and clicking with an empty hand clears the slot.
 */
public class DiamondPipeMenu extends BCMenu {
    public DiamondPipeMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainer(DiamondItemBehaviour.FILTERS_PER_SIDE * 6));
    }

    public DiamondPipeMenu(int id, Inventory inventory, BlockPos pos, Container filters) {
        super(BCTransport.DIAMOND_PIPE_MENU.get(), id, pos);
        addFullPlayerInventory(inventory, 140);
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 9; x++) {
                addSlot(new PhantomSlot(filters, x + y * 9, 8 + x * 18, 18 + y * 18));
            }
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof PhantomSlot phantom) {
            ItemStack carried = getCarried();
            ItemStack current = phantom.getItem();
            if (carried.isEmpty()) {
                phantom.set(ItemStack.EMPTY);
            } else if (!ItemStack.isSameItemSameTags(carried, current)) {
                ItemStack copy = carried.copy();
                copy.setCount(1);
                phantom.set(copy);
            } else if (current.getCount() < current.getMaxStackSize()) {
                ItemStack grown = current.copy();
                grown.grow(1);
                phantom.set(grown);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    static final class PhantomSlot extends Slot {
        PhantomSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
    }
}
