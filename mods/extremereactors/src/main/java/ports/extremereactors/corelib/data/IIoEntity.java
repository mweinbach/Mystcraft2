/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data;

public interface IIoEntity {

    IoDirection getIoDirection();

    default void setIoDirection(final IoDirection direction) {
    }

    default void toggleIoDirection() {
        this.setIoDirection(this.getIoDirection().opposite());
    }
}
