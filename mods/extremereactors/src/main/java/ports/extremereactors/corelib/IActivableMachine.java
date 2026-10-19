/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib;

public interface IActivableMachine {

    boolean isMachineActive();

    void setMachineActive(boolean active);

    default void toggleMachineActive() {
        this.setMachineActive(!this.isMachineActive());
    }
}
