/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.layout;

public enum VerticalAlignment {

    Top,
    Center,
    Bottom;

    /** The y of something {@code size} high aligned inside {@code [start, start + room)}. */
    public int align(final int start, final int room, final int size) {
        return switch (this) {
            case Top -> start;
            case Center -> start + (room - size) / 2;
            case Bottom -> start + room - size;
        };
    }
}
