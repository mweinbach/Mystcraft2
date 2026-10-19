package net.mystcraft.minecraft.client;

/** Source GuiElementVSlider arithmetic, independent of rendering and inventory transactions. */
public final class ClassicPageScroll {
    private int pixels, maximum;
    private boolean dragging;
    public int pixels() { return pixels; }
    public int maximum() { return maximum; }
    public boolean dragging() { return dragging; }
    public void reset() { pixels = 0; dragging = false; }
    public void layout(int entries, int height) {
        int rows = (Math.max(0, entries) + 4) / 5;
        int bottom = rows == 0 ? 0 : (rows - 1) * 41 + 40;
        maximum = bottom > height ? bottom + 6 - height : 0;
        pixels = Math.min(pixels, maximum);
    }
    public void wheel(double delta) { pixels = clamp(pixels - (int)Math.signum(delta) * 20); }
    public void begin(double mouseY, int top, int height) { dragging = true; drag(mouseY, top, height); }
    public void drag(double mouseY, int top, int height) {
        if (dragging) pixels = clamp((int)(Math.max(0, Math.min(1, (mouseY - top - 11.5) / Math.max(1, height - 23))) * maximum));
    }
    public void end() { dragging = false; }
    public int cell(double x, double y, int left, int top, int height) {
        double gx = x - left, gy = y - top;
        if (gx < 0 || gx >= 155 || gy < 0 || gy >= height) return -1;
        int column = (int)gx / 31, contentY = (int)gy + pixels;
        if ((int)gx % 31 >= 30 || contentY % 41 >= 40) return -1;
        return contentY / 41 * 5 + column;
    }
    private int clamp(int value) { return Math.max(0, Math.min(maximum, value)); }
}
