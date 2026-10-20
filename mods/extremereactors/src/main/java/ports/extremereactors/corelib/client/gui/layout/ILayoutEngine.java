/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.layout;

import ports.extremereactors.corelib.client.gui.IControlContainer;

/**
 * Sizes and places the active children of a container, setting their bounds relative to the container.
 */
@FunctionalInterface
public interface ILayoutEngine {

    void layout(IControlContainer container);
}
