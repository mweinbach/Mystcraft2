package net.mystcraft.minecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.mystcraft.minecraft.ink.InkMixerMenu;
import net.mystcraft.minecraft.ink.InkAdditives;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Original 176x181 classic mixer layout and art, with server-synchronized basin state. */
public final class InkMixerScreen extends AbstractContainerScreen<InkMixerMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("mystcraft", "textures/gui/inkmixer.png");

    public InkMixerScreen(InkMixerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 181;
        inventoryLabelY = 87;
        titleLabelY = 5;
    }

    @Override protected void renderBg(PoseStack pose, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, TEXTURE);
        blit(pose, leftPos + 54, topPos + 16, 179, 16, 66, 65);
        if (menu.hasInk()) {
            // The source GUI masks its ink fluid through the transparent circular basin.
            fill(pose, leftPos + 55, topPos + 17, leftPos + 120, topPos + 81, basinColor());
        }
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.enableBlend();
        blit(pose, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        RenderSystem.disableBlend();
    }

    private int basinColor() {
        int[] colors = {0x00ff00, 0xffffff, 0x808080, 0x0000ff, 0xff0000, 0x990099};
        float weight = 0, red = 0, green = 0, blue = 0;
        for (int i = 0; i < colors.length; i++) {
            float p = menu.probability(i);
            weight += p;
            red += ((colors[i] >> 16) & 255) * p;
            green += ((colors[i] >> 8) & 255) * p;
            blue += (colors[i] & 255) * p;
        }
        if (weight == 0) return 0xff17131e;
        return 0xff000000 | ((int) (red / weight * .65F) << 16) | ((int) (green / weight * .65F) << 8) | (int) (blue / weight * .65F);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        double dx = x - leftPos - 88, dy = y - topPos - 49;
        if (dx * dx + dy * dy < 900 && !menu.getCarried().isEmpty() && (button == 0 || button == 1)) {
            net.mystcraft.integration.network.MenuActions.send(menu.containerId, button);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        renderTooltip(pose, mouseX, mouseY);
        int dx = mouseX - leftPos - 88, dy = mouseY - topPos - 49;
        if (menu.hasInk() && dx * dx + dy * dy < 900) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.mystcraft.ink_mixer.additives"));
            for (int i = 0; i < InkAdditives.PROPERTIES.size(); i++) if (menu.probability(i) > 0)
                lines.add(Component.literal(InkAdditives.PROPERTIES.get(i) + ": " + Math.round(menu.probability(i) * 100) + "%"));
            renderComponentTooltip(pose, lines, mouseX, mouseY);
        }
    }
}
