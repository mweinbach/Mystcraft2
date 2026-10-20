/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.databind;

/**
 * Reads a value from a source and hands it to the controls that show it.
 */
@FunctionalInterface
public interface IBinding {

    /** Read the value again and, if it changed (or was never read), pass it on. */
    void update();
}
