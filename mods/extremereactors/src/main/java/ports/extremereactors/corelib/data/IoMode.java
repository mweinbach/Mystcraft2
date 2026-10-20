/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data;

/** Active ports push or pull on their own; passive ports wait for neighbours to do it. */
public enum IoMode {

    Active,
    Passive;

    public boolean isActive() {
        return this == Active;
    }

    public boolean isPassive() {
        return this == Passive;
    }
}
