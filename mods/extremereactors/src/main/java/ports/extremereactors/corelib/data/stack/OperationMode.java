/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.stack;

public enum OperationMode {

    Execute,
    Simulate;

    public boolean execute() {
        return this == Execute;
    }

    public boolean simulate() {
        return this == Simulate;
    }
}
