/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.capabilities;

import ports.projecte.lib.energy.IEnergyStorage;
import ports.projecte.lib.fluids.capability.IFluidHandler;
import ports.projecte.lib.fluids.capability.IFluidHandlerItem;
import ports.projecte.lib.items.IItemHandler;

/**
 * The loader's own transfer capabilities. Each loader maps them at its edges: Forge to Forge Energy and Forge's
 * item and fluid handlers, Fabric to Team Reborn Energy and the Fabric Transfer API.
 */
public final class ForgeCapabilities {

    public static final Capability<IEnergyStorage> ENERGY = CapabilityManager.get(IEnergyStorage.class);
    public static final Capability<IFluidHandler> FLUID_HANDLER = CapabilityManager.get(IFluidHandler.class);
    public static final Capability<IFluidHandlerItem> FLUID_HANDLER_ITEM = CapabilityManager.get(IFluidHandlerItem.class);
    public static final Capability<IItemHandler> ITEM_HANDLER = CapabilityManager.get(IItemHandler.class);

    private ForgeCapabilities() {
    }
}
