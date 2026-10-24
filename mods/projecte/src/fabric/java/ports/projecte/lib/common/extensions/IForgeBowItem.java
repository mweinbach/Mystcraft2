/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.extensions;

import net.minecraft.world.entity.projectile.AbstractArrow;

/**
 * Forge's {@code BowItem.customArrow}, injected into {@link net.minecraft.world.item.BowItem} on Fabric.
 */
public interface IForgeBowItem {

    default AbstractArrow customArrow(AbstractArrow arrow) {
        return arrow;
    }
}
