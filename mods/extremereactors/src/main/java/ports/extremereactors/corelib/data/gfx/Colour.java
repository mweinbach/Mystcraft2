/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.gfx;

/** An immutable RGBA colour with 8-bit channels. */
public record Colour(int red, int green, int blue, int alpha) {

    public static final Colour BLACK = new Colour(0, 0, 0, 255);
    public static final Colour WHITE = new Colour(255, 255, 255, 255);

    public Colour {

        red &= 0xFF;
        green &= 0xFF;
        blue &= 0xFF;
        alpha &= 0xFF;
    }

    public static Colour fromRGB(final int rgb) {
        return new Colour(rgb >> 16, rgb >> 8, rgb, 255);
    }

    public static Colour fromRGBA(final int rgba) {
        return new Colour(rgba >> 24, rgba >> 16, rgba >> 8, rgba);
    }

    public static Colour fromARGB(final int argb) {
        return new Colour(argb >> 16, argb >> 8, argb, argb >> 24);
    }

    public int toRGB() {
        return (this.red << 16) | (this.green << 8) | this.blue;
    }

    public int toARGB() {
        return (this.alpha << 24) | this.toRGB();
    }

    public int toRGBA() {
        return (this.toRGB() << 8) | this.alpha;
    }

    public float glRed() {
        return this.red / 255.0f;
    }

    public float glGreen() {
        return this.green / 255.0f;
    }

    public float glBlue() {
        return this.blue / 255.0f;
    }

    public float glAlpha() {
        return this.alpha / 255.0f;
    }
}
