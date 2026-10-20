/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib;

import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandler;
import net.minecraft.world.item.ItemStack;

public final class DebuggableHelper {

    public static void getDebugMessagesFor(final IDebugMessages messages, final IItemHandler handler) {

        final int slots = handler.getSlots();

        messages.addUnlocalized("Slots: %d", slots);

        for (int slot = 0; slot < slots; ++slot) {

            final ItemStack stack = handler.getStackInSlot(slot);

            messages.addUnlocalized("[%d] %s", slot, stack.isEmpty() ? "empty" : stack.getCount() + " x " + stack.getItem());
        }
    }

    public static void getDebugMessagesFor(final IDebugMessages messages, final IFluidHandler handler) {

        final int tanks = handler.getTanks();

        messages.addUnlocalized("Tanks: %d", tanks);

        for (int tank = 0; tank < tanks; ++tank) {

            final FluidStack stack = handler.getFluidInTank(tank);

            messages.addUnlocalized("[%d] %s / %d mB", tank,
                    stack.isEmpty() ? "empty" : stack.getAmount() + " mB of " + CodeHelper.getObjectId(stack.getFluid()),
                    handler.getTankCapacity(tank));
        }
    }

    private DebuggableHelper() {
    }
}
