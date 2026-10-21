/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.gui.overlay;

import com.mojang.blaze3d.vertex.PoseStack;

@FunctionalInterface
public interface IGuiOverlay {

    void render(ForgeGui gui, PoseStack poseStack, float partialTick, int screenWidth, int screenHeight);
}
