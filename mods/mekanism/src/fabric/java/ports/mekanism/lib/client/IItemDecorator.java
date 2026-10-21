/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client;

import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;

/**
 * Draws over an item in a slot after its count and durability bar.
 */
public interface IItemDecorator {

    boolean render(Font font, ItemStack stack, int xOffset, int yOffset, float blitOffset);
}
