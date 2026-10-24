/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.extensions;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluid;

/**
 * Forge's {@code BucketItem.getFluid}, injected into {@link BucketItem} on Fabric.
 */
public interface IForgeBucketItem {

    default Fluid projecte$getFluid() {
        return ((BucketItem) this).content;
    }
}
