/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui;

import net.minecraft.network.chat.Component;
import ports.extremereactors.corelib.client.gui.layout.ILayoutEngine;

import java.util.List;
import java.util.function.Consumer;

/**
 * A control that holds other controls and lays them out with an {@link ILayoutEngine}.
 */
public interface IControlContainer
        extends IControl {

    void addControl(IControl... controls);

    void removeControl(IControl control);

    void removeControls();

    /** The children, in the order they were added. */
    List<IControl> getControls();

    /** The children that are laid out, painted and receive input; all of them unless the container chooses. */
    default List<IControl> getActiveControls() {
        return this.getControls();
    }

    ILayoutEngine getLayoutEngine();

    void setLayoutEngine(ILayoutEngine engine);

    /** Size and place the children, then let child containers lay out theirs. */
    void layout();

    /**
     * Check the values of the controls this container shows, reporting each problem.
     */
    void validate(Consumer<Component> errorReport);
}
