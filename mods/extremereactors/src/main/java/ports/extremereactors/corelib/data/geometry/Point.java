/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.geometry;

/**
 * An immutable 2D integer point.
 */
public final class Point {

    public static final Point ZERO = new Point(0, 0);

    public final int X;
    public final int Y;

    public Point(final int x, final int y) {

        this.X = x;
        this.Y = y;
    }

    public Point offset(final int x, final int y) {
        return 0 == x && 0 == y ? this : new Point(this.X + x, this.Y + y);
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof Point point && point.X == this.X && point.Y == this.Y;
    }

    @Override
    public int hashCode() {
        return 31 * this.X + this.Y;
    }

    @Override
    public String toString() {
        return "(" + this.X + ", " + this.Y + ")";
    }
}
