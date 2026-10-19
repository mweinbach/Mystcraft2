package ports.jurassicraft.assembly.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.assembly.SkeletonAssemblyMenu;

public final class SkeletonAssemblyScreen extends AbstractContainerScreen<SkeletonAssemblyMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(PortMod.MOD_ID, "textures/gui/skeleton_assembler.png");
    public SkeletonAssemblyScreen(SkeletonAssemblyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 201;
    }
    @Override protected void init() { super.init(); titleLabelX = (imageWidth - font.width(title)) / 2; titleLabelY = 4; }
    @Override protected void renderLabels(PoseStack pose, int mouseX, int mouseY) { font.draw(pose, title, titleLabelX, titleLabelY, 4210752); }
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
