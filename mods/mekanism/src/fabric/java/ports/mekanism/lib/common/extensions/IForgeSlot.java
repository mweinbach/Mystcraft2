/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.extensions;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;

/**
 * Methods Forge adds to {@link Slot}, injected into it on Fabric. The background is stored by a mixin that also
 * returns it from {@link Slot#getNoItemIcon}.
 */
public interface IForgeSlot {

    default int getSlotIndex() {
        return ((Slot) this).getContainerSlot();
    }

    default Slot setBackground(ResourceLocation atlas, ResourceLocation sprite) {
        throw new IllegalStateException("Slot mixin not applied");
    }
}
