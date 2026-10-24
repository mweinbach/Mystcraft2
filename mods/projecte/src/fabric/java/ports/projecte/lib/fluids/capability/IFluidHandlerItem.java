/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.fluids.capability;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public interface IFluidHandlerItem extends IFluidHandler {

    /**
     * The container after the fills and drains so far; it may be a different item, such as an empty bucket.
     */
    @NotNull
    ItemStack getContainer();
}
