package ports.jurassicraft.cleaning.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.cleaning.CleaningStationMenu;

public final class CleaningStationScreen extends AbstractContainerScreen<CleaningStationMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(PortMod.MOD_ID, "textures/gui/cleaning_station.png");

    public CleaningStationScreen(CleaningStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
    }

    @Override public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        renderTooltip(pose, mouseX, mouseY);
    }

    @Override protected void renderBg(PoseStack pose, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        blit(pose, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int water = menu.waterPixels(51);
        if (water > 0) blit(pose, leftPos + 46, topPos + 18 + 51 - water, 176, 81 - water, 14, water + 1);
        int progress = menu.progressPixels(24);
        if (progress > 0) blit(pose, leftPos + 79, topPos + 34, 176, 14, progress + 1, 16);
    }
}
