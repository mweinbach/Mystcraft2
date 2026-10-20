package ports.buildcraft.silicon;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.lib.BCMenu;
import ports.buildcraft.lib.LongData;

/** The laser table menus: the table's power target, stored power and average rate, plus upstream's slot kinds. */
public abstract class LaserTableMenu extends BCMenu {
    @Nullable protected final LaserTableBlockEntity table;
    private final LongData powerData;

    protected LaserTableMenu(MenuType<?> type, int id, BlockPos pos, @Nullable LaserTableBlockEntity table) {
        super(type, id, pos);
        this.table = table;
        powerData = table == null ? LongData.client(3) : LongData.server(table::getTarget, table::power, table::averagePowerRounded);
        addDataSlots(powerData);
    }

    public long target() {
        return powerData.getLong(0);
    }

    public long power() {
        return powerData.getLong(1);
    }

    public long averagePower() {
        return powerData.getLong(2);
    }

    /** Called on the server for a click on a display slot. */
    protected void displayClicked(int index, int button, ClickType clickType, Player player) {}

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slots.size()) {
            Slot slot = slots.get(slotId);
            if (slot instanceof DisplaySlot) {
                if (!player.level.isClientSide) displayClicked(slot.getContainerSlot(), button, clickType, player);
                return;
            }
            if (slot instanceof PhantomSlot phantom) {
                ItemStack carried = getCarried();
                if (carried.isEmpty()) {
                    phantom.set(ItemStack.EMPTY);
                } else if (!ItemStack.isSameItemSameTags(carried, phantom.getItem())) {
                    ItemStack copy = carried.copy();
                    copy.setCount(1);
                    phantom.set(copy);
                }
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    /** Upstream {@code SlotDisplay}: shows a stack that cannot be taken or replaced. */
    static final class DisplaySlot extends Slot {
        DisplaySlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
    }

    /** Upstream {@code SlotPhantom} without count adjustment: a click sets a single copy of the held item, or clears it. */
    static final class PhantomSlot extends Slot {
        PhantomSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
    }

    /** Upstream {@code SlotOutput}: items can only be taken. */
    static final class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
