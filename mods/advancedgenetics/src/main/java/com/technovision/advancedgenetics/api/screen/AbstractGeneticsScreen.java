package com.technovision.advancedgenetics.api.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.technovision.advancedgenetics.AdvancedGenetics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared machine screen: background texture, progress arrow, energy bar and overclock count.
 */
public abstract class AbstractGeneticsScreen<M extends AbstractGeneticsScreenHandler> extends AbstractContainerScreen<M> {

    private static final ResourceLocation WIDGETS = new ResourceLocation(AdvancedGenetics.MOD_ID, "textures/gui/cell_analyzer_gui.png");

    private final ResourceLocation texture;
    protected final List<DisplayData> displayData = new ArrayList<>();

    public AbstractGeneticsScreen(M handler, Inventory inventory, Component title, ResourceLocation texture) {
        super(handler, inventory, title);
        this.texture = texture;
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
        displayData.add(new ProgressDisplayData(handler.getPropertyDelegate(), 0, 1, 83, 37, 60, 9, Direction2D.RIGHT));
        displayData.add(new EnergyDisplayData(handler.getPropertyDelegate(), 2, 3, 10, 23, 12, 40));
    }

    @Override
    protected void renderBg(PoseStack matrices, float delta, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, texture);
        blit(matrices, this.leftPos, this.topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(PoseStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        renderDisplayData(displayData, matrices, this.leftPos, this.topPos);
        renderDisplayTooltip(displayData, matrices, this.leftPos, this.topPos, mouseX, mouseY);
        renderTooltip(matrices, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(PoseStack matrices, int mouseX, int mouseY) {
        super.renderLabels(matrices, mouseX, mouseY);
        int overclock = menu.getPropertyDelegate().get(4);
        if (overclock > 0) {
            String text = "x"+overclock;
            font.draw(matrices, text, imageWidth - font.width(text) - 8, 6, 0x3f3f3f);
        }
    }

    public void renderDisplayTooltip(List<DisplayData> displayData, PoseStack matrices, int x, int y, int mouseX, int mouseY) {
        displayData.stream().filter(data ->
                mouseX >= data.getX() + x &&
                        mouseX <= data.getX() + x + data.getWidth() &&
                        mouseY >= data.getY() + y &&
                        mouseY <= data.getY() + y + data.getHeight()
        ).forEach(data -> {
            if (!(data instanceof ProgressDisplayData)) {
                renderComponentTooltip(matrices, data.toText(), mouseX, mouseY);
            }
        });
    }

    public void renderDisplayData(List<DisplayData> displayData, PoseStack matrices, int x, int y) {
        displayData.forEach(data -> {
            if (data instanceof ProgressDisplayData progressData) {
                directionalArrow(matrices, x + data.getX(), y + data.getY(), data.getValue(), data.getMaxValue(), progressData.getDirection());
            }
            if (data instanceof EnergyDisplayData energyData) {
                drawEnergyBar(matrices, energyData, 178, 4);
            }
        });
    }

    public void drawEnergyBar(PoseStack pPoseStack, EnergyDisplayData data, int textureX, int textureY) {
        int x = data.getX() + (this.width - this.imageWidth) / 2;
        int y = data.getY() + (this.height - this.imageHeight) / 2;
        this.directionalBlit(pPoseStack, x, y + data.getHeight(), textureX, textureY, data.getWidth(), data.getHeight(), data.getValue(), data.getMaxValue(), Direction2D.UP);
    }

    public void directionalArrow(PoseStack matrices, int x, int y, int progress, int maxProgress, Direction2D direction2D) {
        switch (direction2D) {
            case LEFT -> directionalBlit(matrices, x, y, 0, 120, 9, 30, progress, maxProgress, Direction2D.LEFT);
            case UP -> directionalBlit(matrices, x, y, 0, 138, 9, 30, progress, maxProgress, Direction2D.UP);
            case RIGHT -> directionalBlit(matrices, x, y, 177, 61, 17, 24, progress, maxProgress, Direction2D.RIGHT);
            case DOWN -> directionalBlit(matrices, x, y, 9, 138, 9, 30, progress, maxProgress, Direction2D.DOWN);
        }
    }

    private void directionalBlit(PoseStack matrices, int x, int y, int uOffset, int vOffset, int u, int v, int progress, int maxProgress, Direction2D direction2D) {
        RenderSystem.setShaderTexture(0, WIDGETS);

        switch (direction2D) {
            case LEFT -> {
                int scaled = getBarScaled(v, progress, maxProgress);
                blit(matrices, x - scaled, y, uOffset + u - scaled, vOffset, scaled, v);
            }
            case UP -> {
                int scaled = getBarScaled(v, progress, maxProgress);
                blit(matrices, x, y - scaled, uOffset, vOffset + v - scaled, u, scaled);
            }
            case RIGHT -> {
                int scaled = getBarScaled(v, progress, maxProgress);
                blit(matrices, x, y, uOffset, vOffset, scaled, u);
            }
            case DOWN -> {
                int scaled = getBarScaled(v, progress, maxProgress);
                blit(matrices, x, y, uOffset, vOffset, u, scaled);
            }
        }
    }

    public static int getBarScaled(int pixels, int progress, int maxProgress) {
        if (progress > 0 && maxProgress > 0) {
            return progress * pixels / maxProgress;
        } else {
            return 0;
        }
    }
}
