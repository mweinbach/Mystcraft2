/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui;

/**
 * Empty space inside the edges of a control, in pixels.
 */
public record Padding(int left, int right, int top, int bottom) {

    public static final Padding ZERO = new Padding(0, 0, 0, 0);

    public Padding {

        left = Math.max(0, left);
        right = Math.max(0, right);
        top = Math.max(0, top);
        bottom = Math.max(0, bottom);
    }

    public static Padding get(final int all) {
        return 0 == all ? ZERO : new Padding(all, all, all, all);
    }

    public static Padding get(final int left, final int right, final int top, final int bottom) {
        return new Padding(left, right, top, bottom);
    }

    public int horizontal() {
        return this.left + this.right;
    }

    public int vertical() {
        return this.top + this.bottom;
    }
}
