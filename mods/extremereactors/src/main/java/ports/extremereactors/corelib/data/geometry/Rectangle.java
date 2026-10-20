/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.geometry;

/**
 * An immutable 2D integer rectangle: an origin and a size. Sizes are never negative.
 */
public final class Rectangle {

    public static final Rectangle ZERO = new Rectangle(0, 0, 0, 0);

    public final Point Origin;
    public final int Width;
    public final int Height;

    public Rectangle(final int x, final int y, final int width, final int height) {

        this.Origin = new Point(x, y);
        this.Width = Math.max(0, width);
        this.Height = Math.max(0, height);
    }

    public int getX1() {
        return this.Origin.X;
    }

    public int getY1() {
        return this.Origin.Y;
    }

    /** The first column past the right edge. */
    public int getX2() {
        return this.Origin.X + this.Width;
    }

    /** The first row past the bottom edge. */
    public int getY2() {
        return this.Origin.Y + this.Height;
    }

    public boolean contains(final int x, final int y) {
        return x >= this.getX1() && x < this.getX2() && y >= this.getY1() && y < this.getY2();
    }

    public boolean contains(final double x, final double y) {
        return x >= this.getX1() && x < this.getX2() && y >= this.getY1() && y < this.getY2();
    }

    public boolean isEmpty() {
        return 0 == this.Width || 0 == this.Height;
    }

    public Rectangle offset(final int x, final int y) {
        return new Rectangle(this.Origin.X + x, this.Origin.Y + y, this.Width, this.Height);
    }

    /** This rectangle shrunk by the given amount on each side. */
    public Rectangle inset(final int left, final int right, final int top, final int bottom) {
        return new Rectangle(this.Origin.X + left, this.Origin.Y + top,
                this.Width - left - right, this.Height - top - bottom);
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof Rectangle rect && rect.Origin.equals(this.Origin) &&
                rect.Width == this.Width && rect.Height == this.Height;
    }

    @Override
    public int hashCode() {
        return (this.Origin.hashCode() * 31 + this.Width) * 31 + this.Height;
    }

    @Override
    public String toString() {
        return this.Origin + " " + this.Width + "x" + this.Height;
    }
}
