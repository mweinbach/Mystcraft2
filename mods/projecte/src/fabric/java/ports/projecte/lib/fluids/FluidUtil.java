/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.fluids;

import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import ports.projecte.lib.common.capabilities.ForgeCapabilities;
import ports.projecte.lib.common.util.LazyOptional;
import ports.projecte.lib.fluids.capability.IFluidHandler.FluidAction;
import ports.projecte.lib.fluids.capability.IFluidHandlerItem;
import ports.projecte.lib.items.ItemHandlerHelper;

public final class FluidUtil {

    private FluidUtil() {
    }

    /**
     * The fluid handler of an item stack: ProjectE's own, or a view of the item's Fabric transfer API storage.
     */
    public static LazyOptional<IFluidHandlerItem> getFluidHandler(ItemStack itemStack) {
        return itemStack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
    }

    public static Optional<FluidStack> getFluidContained(@NotNull ItemStack container) {
        if (container.isEmpty()) {
            return Optional.empty();
        }
        return getFluidHandler(ItemHandlerHelper.copyStackWithSize(container, 1)).map(handler -> handler.drain(Integer.MAX_VALUE, FluidAction.SIMULATE))
              .filter(stack -> !stack.isEmpty());
    }
}
