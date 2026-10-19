package net.mystcraft.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.mystcraft.integration.network.MenuActions;
import net.mystcraft.minecraft.exploration.BookDisplayMenu;
import net.mystcraft.minecraft.item.LinkingBookItem;

public final class BookDisplayScreen extends AbstractContainerScreen<BookDisplayMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("mystcraft", "textures/gui/single_slot.png");
    private Button read;
    private boolean requestedBook;
    public BookDisplayScreen(BookDisplayMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); imageWidth = 176; imageHeight = 166; }
    @Override protected void init() {
        super.init();
        read = addRenderableWidget(new Button(leftPos + 108, topPos + 34, 58, 20, Component.translatable("button.mystcraft.read_book"), button -> MenuActions.send(menu.containerId, BookDisplayMenu.READ)));
    }
    @Override protected void containerTick() {
        super.containerTick();
        read.visible = menu.displayed().getItem() instanceof LinkingBookItem;
        if (read.visible && !requestedBook) { requestedBook = true; MenuActions.send(menu.containerId, BookDisplayMenu.READ); }
        if (!read.visible) requestedBook = false;
    }
    @Override protected void renderBg(PoseStack pose, float partial, int mouseX, int mouseY) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, TEXTURE);
        blit(pose, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }
    @Override public void render(PoseStack pose, int mouseX, int mouseY, float partial) { renderBackground(pose); super.render(pose, mouseX, mouseY, partial); renderTooltip(pose, mouseX, mouseY); }
}
