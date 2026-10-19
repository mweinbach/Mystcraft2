package net.mystcraft.minecraft.age.visual;

/** RenderRainbow's 50 HSV bands (value200, saturation255), independent of time and weather. */
public final class RainbowMath {
    private RainbowMath() {}
    public static float height(float z) { return (float) Math.sqrt(Math.max(0, 1 - z / 100F * (z / 100F))) * 100; }
    public static int color(int band) {
        if (band < 0 || band >= 50) throw new IllegalArgumentException("Invalid rainbow band");
        float progress = (band / 50F * 240F) / 360F;
        float segment = 1F / 6F;
        int r = 0, g = 0, b = 0;
        if (progress < segment) { r = 255; g = (int) (progress / segment * 255); }
        else if (progress < 2 * segment) { r = 255 - (int) ((progress - segment) / segment * 255); g = 255; }
        else if (progress < 3 * segment) { g = 255; b = (int) ((progress - 2 * segment) / segment * 255); }
        else { g = 255 - (int) ((progress - 3 * segment) / segment * 255); b = 255; }
        return ((int) (r / 255F * 200) << 16) | ((int) (g / 255F * 200) << 8) | (int) (b / 255F * 200);
    }
}
