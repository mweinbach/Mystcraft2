package ports.buildcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Upstream {@code GuiBC8}: a fixed background texture with a centred title and the inventory label. */
abstract class BCScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    protected final ResourceLocation texture;

    BCScreen(M menu, Inventory inventory, Component title, ResourceLocation texture, int width, int height) {
        super(menu, inventory, title);
        this.texture = texture;
        imageWidth = width;
        imageHeight = height;
        inventoryLabelY = height - 94;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);
        renderTooltip(poseStack, mouseX, mouseY);
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, texture);
        blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        renderExtras(poseStack, partialTicks);
    }

    protected void renderExtras(PoseStack poseStack, float partialTicks) {}
}
