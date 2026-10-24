/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.extensions;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * The getters Forge adds to {@link AbstractContainerScreen}, injected into it on Fabric.
 */
public interface IForgeAbstractContainerScreen {

    private AbstractContainerScreen<?> self() {
        return (AbstractContainerScreen<?>) this;
    }

    @Nullable
    default Slot getSlotUnderMouse() {
        return self().hoveredSlot;
    }

    default int getGuiLeft() {
        return self().leftPos;
    }

    default int getGuiTop() {
        return self().topPos;
    }

    default int getXSize() {
        return self().imageWidth;
    }

    default int getYSize() {
        return self().imageHeight;
    }
}
