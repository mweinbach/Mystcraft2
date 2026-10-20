/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui;

/**
 * The direction a gauge fills in.
 */
public enum Orientation {

    LeftToRight,
    RightToLeft,
    TopToBottom,
    BottomToTop;

    public boolean isHorizontal() {
        return LeftToRight == this || RightToLeft == this;
    }
}
