package com.technovision.advancedgenetics.api.screen.slot;

import com.technovision.advancedgenetics.common.item.CellItem;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CellSlot extends Slot {

    public CellSlot(Container inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.getItem() instanceof CellItem;
    }
}
