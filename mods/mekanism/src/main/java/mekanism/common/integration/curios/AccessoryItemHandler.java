package mekanism.common.integration.curios;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only view of accessory stacks. Charging writes into the stack itself, so the list holds the live stacks.
 */
public final class AccessoryItemHandler implements IItemHandler {

    private final List<ItemStack> stacks;

    public AccessoryItemHandler(List<ItemStack> stacks) {
        this.stacks = stacks;
    }

    @Override
    public int getSlots() {
        return stacks.size();
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return stacks.get(slot);
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        return stack;
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return true;
    }
}
