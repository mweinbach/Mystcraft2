/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import net.minecraft.network.chat.Component;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import javax.annotation.Nullable;

/**
 * A switch that shows an icon for each state, usually over a background sprite.
 */
public class SwitchPictureButton
        extends AbstractSwitchButton<SwitchPictureButton> {

    public SwitchPictureButton(final ModContainerScreen<? extends ModContainer> gui, final String name,
                               final boolean active) {
        this(gui, name, active, null);
    }

    public SwitchPictureButton(final ModContainerScreen<? extends ModContainer> gui, final String name,
                               final boolean active, @Nullable final String group) {
        super(gui, name, Component.empty(), active, group);
    }
}
