/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.gui.overlay;

import net.minecraft.resources.ResourceLocation;

public record NamedGuiOverlay(ResourceLocation id, IGuiOverlay overlay) {
}
