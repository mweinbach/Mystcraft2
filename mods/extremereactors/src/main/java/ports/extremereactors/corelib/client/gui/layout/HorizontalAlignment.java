/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.layout;

public enum HorizontalAlignment {

    Left,
    Center,
    Right;

    /** The x of something {@code size} wide aligned inside {@code [start, start + room)}. */
    public int align(final int start, final int room, final int size) {
        return switch (this) {
            case Left -> start;
            case Center -> start + (room - size) / 2;
            case Right -> start + room - size;
        };
    }
}
