package ports.jurassicraft.grinder;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ports.jurassicraft.PortMod;

public final class FossilGrinderScreen extends AbstractContainerScreen<FossilGrinderMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(PortMod.MOD_ID, "textures/gui/fossil_grinder.png");
    public FossilGrinderScreen(FossilGrinderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        titleLabelX = (imageWidth - font.width(title)) / 2;
    }
    @Override protected void renderBg(PoseStack pose, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, TEXTURE);
        blit(pose, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        blit(pose, leftPos + 79, topPos + 34, 176, 14, menu.progressScaled(24) + 1, 16);
    }
    @Override public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        renderTooltip(pose, mouseX, mouseY);
    }
}
