/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.extensions;

import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Forge's {@code AbstractWidget.getFGColor}, injected into {@link AbstractWidget} on Fabric. Without Forge's packed
 * color override it is vanilla's label color.
 */
public interface IForgeAbstractWidget {

    default int projecte$getFGColor() {
        return ((AbstractWidget) this).active ? 0xFFFFFF : 0xA0A0A0;
    }
}
