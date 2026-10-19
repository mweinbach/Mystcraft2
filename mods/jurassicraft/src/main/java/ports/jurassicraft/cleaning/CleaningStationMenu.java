package ports.jurassicraft.cleaning;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

public final class CleaningStationMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Client constructor; the menu protocol synchronizes slots and data. */
    public CleaningStationMenu(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(id, playerInventory, clientInventory(), new SimpleContainerData(4), buffer.readBlockPos());
    }

    public CleaningStationMenu(int id, Inventory playerInventory, CleaningStationBlockEntity station) {
        this(id, playerInventory, station.inventory(), station.data(), station.getBlockPos());
    }

    private CleaningStationMenu(int id, Inventory playerInventory, IItemHandler station, ContainerData data, BlockPos pos) {
        super(CleaningStationRegistry.MENU.get(), id);
        this.access = ContainerLevelAccess.create(playerInventory.player.level, pos);
        this.data = data;
        checkContainerDataCount(data, 4);
        addSlot(new SlotItemHandler(station, CleaningStationBlockEntity.INPUT, 56, 17));
        addSlot(new SlotItemHandler(station, CleaningStationBlockEntity.FUEL, 56, 53));
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new SlotItemHandler(station, 2 + row * 3 + column, 108 + column * 18, 26 + row * 18) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
    }

    private static ItemStackHandler clientInventory() {
        return new ItemStackHandler(CleaningStationBlockEntity.SLOT_COUNT) {
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return slot == CleaningStationBlockEntity.INPUT ? CleaningStationBlockEntity.isCleanable(stack)
                        : slot == CleaningStationBlockEntity.FUEL && stack.is(Items.WATER_BUCKET);
            }
        };
    }

    public int waterPixels(int scale) { return data.get(1) == 0 ? 0 : data.get(0) * scale / data.get(1); }
    public int progressPixels(int scale) { return data.get(3) == 0 ? 0 : data.get(2) * scale / data.get(3); }

    @Override public boolean stillValid(Player player) {
        return stillValid(access, player, CleaningStationRegistry.BLOCK.get());
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < CleaningStationBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, 8, 44, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            if (CleaningStationBlockEntity.isCleanable(stack)) moved = moveItemStackTo(stack, 0, 1, false);
            else if (stack.is(Items.WATER_BUCKET)) moved = moveItemStackTo(stack, 1, 2, false);
            if (!moved) {
                if (index < 35) moved = moveItemStackTo(stack, 35, 44, false);
                else moved = moveItemStackTo(stack, 8, 35, false);
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (index < CleaningStationBlockEntity.SLOT_COUNT || stack.isEmpty()) slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
