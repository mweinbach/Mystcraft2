/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.extensions;

import net.minecraft.world.item.Item;

/**
 * Forge's {@code Item.Properties.setNoRepair}, injected into {@link Item.Properties} on Fabric. Forge's flag only
 * stops damageable items from being repaired; every Mekanism item that sets it has no durability, so vanilla never
 * repairs it either and the flag has nothing to change.
 */
public interface IForgeItemProperties {

    default Item.Properties projecte$setNoRepair() {
        return (Item.Properties) this;
    }
}
