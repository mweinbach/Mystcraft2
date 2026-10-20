package ports.buildcraft.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import ports.buildcraft.BuildCraft;

/**
 * Upstream {@code Ledger_Neptune} on the right of a GUI: a tinted tab showing an icon that slides open to show
 * more when clicked. Whether a ledger is open is remembered by key for the session, as upstream's GUI config did.
 */
final class Ledger {
    static final int GAP = 4;
    static final int CLOSED_WIDTH = 2 + 16 + GAP;
    static final int CLOSED_HEIGHT = GAP + 16 + GAP;
    private static final ResourceLocation TEXTURE = BuildCraft.id("textures/gui/ledger_right.png");
    private static final Set<String> OPEN = new HashSet<>();

    private final String key;
    private final int colour;
    private int x, y;
    private double width = CLOSED_WIDTH, height = CLOSED_HEIGHT;
    private double lastWidth = width, lastHeight = height;

    Ledger(String key, int colour) {
        this.key = key;
        this.colour = colour;
    }

    /** Places the ledger's top-left corner; an open ledger starts fully open. */
    void place(int x, int y, int maxWidth, int maxHeight) {
        this.x = x;
        this.y = y;
        if (isOpen()) {
            width = lastWidth = maxWidth;
            height = lastHeight = maxHeight;
        }
    }

    boolean isOpen() {
        return OPEN.contains(key);
    }

    void toggle() {
        if (!OPEN.remove(key)) OPEN.add(key);
    }

    /** True once the ledger has moved off its closed size, when its contents are drawn. */
    boolean isExpanded() {
        return width > CLOSED_WIDTH || height > CLOSED_HEIGHT;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    /** Where the icon is drawn; contents go to its right, from {@link #innerX()}. */
    int iconX() {
        return x + 2;
    }

    int iconY() {
        return y + GAP;
    }

    int innerX() {
        return iconX() + 16 + GAP;
    }

    void tick(int maxWidth, int maxHeight) {
        lastWidth = width;
        lastHeight = height;
        double targetWidth = isOpen() ? maxWidth : CLOSED_WIDTH;
        double targetHeight = isOpen() ? maxHeight : CLOSED_HEIGHT;
        double step = Mth.clamp(Math.max(maxWidth - CLOSED_WIDTH, maxHeight - CLOSED_HEIGHT) / 5.0, 1, 15);
        width = approach(width, targetWidth, step);
        height = approach(height, targetHeight, step);
    }

    private static double approach(double current, double target, double step) {
        return current < target ? Math.min(target, current + step) : Math.max(target, current - step);
    }

    /** Draws the tab, then {@code contents} clipped to it. */
    void render(PoseStack poseStack, float partialTicks, Runnable contents) {
        int w = (int) Math.round(lastWidth + (width - lastWidth) * partialTicks);
        int h = (int) Math.round(lastHeight + (height - lastHeight) * partialTicks);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShaderColor((colour >> 16 & 0xFF) / 255F, (colour >> 8 & 0xFF) / 255F, (colour & 0xFF) / 255F, 1);
        nineSlice(poseStack, x, y, w, h, 0, 0, 16, 4, 16);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        scissor(iconX(), iconY(), w - 4, h - 8);
        contents.run();
        RenderSystem.disableScissor();
    }

    /** Upstream {@code SpriteNineSliced}: corners stay their size, edges and centre stretch. */
    static void nineSlice(PoseStack poseStack, int x, int y, int width, int height, int u, int v, int size, int border, int textureSize) {
        int inner = size - border * 2;
        int[] xs = {x, x + border, x + width - border, x + width};
        int[] ys = {y, y + border, y + height - border, y + height};
        int[] us = {u, u + border, u + border + inner};
        int[] uw = {border, inner, border};
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                int w = xs[col + 1] - xs[col];
                int h = ys[row + 1] - ys[row];
                if (w <= 0 || h <= 0) continue;
                GuiComponent.blit(poseStack, xs[col], ys[row], w, h, us[col], v + us[row] - u, uw[col], uw[row], textureSize, textureSize);
            }
        }
    }

    private static void scissor(int x, int y, int width, int height) {
        Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        RenderSystem.enableScissor((int) (x * scale), (int) (window.getHeight() - (y + height) * scale),
                Math.max(0, (int) (width * scale)), Math.max(0, (int) (height * scale)));
    }
}
