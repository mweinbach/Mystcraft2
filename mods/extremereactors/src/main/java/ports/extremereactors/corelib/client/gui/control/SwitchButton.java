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
 * A switch with a text.
 */
public class SwitchButton
        extends AbstractSwitchButton<SwitchButton> {

    public SwitchButton(final ModContainerScreen<? extends ModContainer> gui, final String name, final String text,
                        final boolean active) {
        this(gui, name, text, active, null);
    }

    public SwitchButton(final ModContainerScreen<? extends ModContainer> gui, final String name, final String text,
                        final boolean active, @Nullable final String group) {
        super(gui, name, Component.literal(text), active, group);
    }

    public SwitchButton(final ModContainerScreen<? extends ModContainer> gui, final String name,
                        final Component text, final boolean active, @Nullable final String group) {
        super(gui, name, text, active, group);
    }
}
