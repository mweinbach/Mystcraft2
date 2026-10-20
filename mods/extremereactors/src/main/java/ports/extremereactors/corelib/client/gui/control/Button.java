/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import net.minecraft.network.chat.Component;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.event.Event;
import ports.extremereactors.corelib.event.IEvent;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import java.util.function.BiConsumer;

/**
 * A push button. {@link #Clicked} receives the button and the mouse button that clicked it.
 */
public class Button
        extends AbstractButtonControl {

    public final IEvent<BiConsumer<Button, Integer>> Clicked;

    public Button(final ModContainerScreen<? extends ModContainer> gui, final String name, final String text) {
        this(gui, name, Component.literal(text));
    }

    public Button(final ModContainerScreen<? extends ModContainer> gui, final String name, final Component text) {

        super(gui, name, text);
        this.Clicked = new Event<>();
    }

    //region AbstractButtonControl

    @Override
    protected boolean isActiveState() {
        return false;
    }

    @Override
    protected void onClick(final int mouseButton) {
        this.Clicked.raise(handler -> handler.accept(this, mouseButton));
    }

    //endregion
}
