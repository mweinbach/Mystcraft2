package ports.jurassicraft.feeder.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.feeder.FeederMenu;

public final class FeederScreen extends AbstractContainerScreen<FeederMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(PortMod.MOD_ID, "textures/gui/feeder.png");
    public FeederScreen(FeederMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    @Override protected void init() { super.init(); titleLabelX = (imageWidth - font.width(title)) / 2; }
    @Override public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        renderTooltip(pose, mouseX, mouseY);
    }
    @Override protected void renderBg(PoseStack pose, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, TEXTURE);
        blit(pose, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }
}
