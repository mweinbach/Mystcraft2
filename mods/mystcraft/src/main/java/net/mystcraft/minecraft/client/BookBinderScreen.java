package net.mystcraft.minecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.mystcraft.minecraft.binder.BookBinderBlockEntity;
import net.mystcraft.minecraft.binder.BookBinderMenu;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Original binder layout: title, cover, ordered scrollable page strip and take-to-bind output. */
public final class BookBinderScreen extends AbstractContainerScreen<BookBinderMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("mystcraft", "textures/gui/pagebinder.png");
    private EditBox bookTitle;
    private int firstPage;
    private boolean syncingTitle;
    public BookBinderScreen(BookBinderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 181;
        inventoryLabelY = 87;
    }
    @Override protected void init() {
        super.init();
        bookTitle = new EditBox(font, leftPos + 7, topPos + 9, 116, 14, Component.translatable("gui.mystcraft.binder.title"));
        bookTitle.setMaxLength(21);
        bookTitle.setValue(menu.pendingTitle());
        bookTitle.setResponder(value -> {
            if (syncingTitle) return;
            send(BookBinderMenu.TITLE_CLEAR);
            for (int i = 0; i < value.length(); i++) send(BookBinderMenu.TITLE_CHAR | value.charAt(i));
        });
        addRenderableWidget(bookTitle);
        addRenderableWidget(new Button(leftPos + 127, topPos + 8, 18, 16, Component.literal("<"), b -> firstPage = Math.max(0, firstPage - 1)));
        addRenderableWidget(new Button(leftPos + 150, topPos + 8, 18, 16, Component.literal(">"), b -> firstPage = Math.min(Math.max(0, menu.pageCount() - 4), firstPage + 1)));
    }
    private void send(int command) {
        if (minecraft != null && minecraft.gameMode != null) net.mystcraft.integration.network.MenuActions.send(menu.containerId, command);
    }
    @Override protected void containerTick() {
        super.containerTick();
        bookTitle.tick();
        if (!bookTitle.isFocused() && !bookTitle.getValue().equals(menu.pendingTitle())) {
            syncingTitle = true;
            bookTitle.setValue(menu.pendingTitle());
            syncingTitle = false;
        }
        bookTitle.setTextColor(bookTitle.getValue().isEmpty() ? 0xff7070 : 0xe0e0e0);
        firstPage = Math.min(firstPage, Math.max(0, menu.pageCount() - 1));
    }
    @Override protected void renderBg(PoseStack pose, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, TEXTURE);
        blit(pose, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        for (int visible = 0; visible < 5 && firstPage + visible < menu.pageCount(); visible++) {
            int x = leftPos + 7 + visible * 30;
            int y = topPos + 45;
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderTexture(0, TEXTURE);
            blit(pose, x, y, 176, 0, 30, 40);
            ItemStack page = menu.page(firstPage + visible);
            itemRenderer.renderAndDecorateItem(page, x + 7, y + 10);
            font.draw(pose, Integer.toString(firstPage + visible + 1), x + 4, y + 29, 0x55432b);
        }
        int append = Math.min(5, Math.max(0, menu.pageCount() - firstPage));
        if (append < 5) {
            int x = leftPos + 7 + append * 30;
            fill(pose, x, topPos + 45, x + 29, topPos + 85, 0x22404040);
            font.draw(pose, "+", x + 11, topPos + 61, 0x707070);
        }
        if (menu.pageCount() == 0 || !BookBinderBlockEntity.isPanel(menu.page(0))) font.draw(pose, "!", leftPos + 30, topPos + 31, 0xdd4444);
    }
    @Override protected void renderLabels(PoseStack pose, int mouseX, int mouseY) {
        font.draw(pose, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (x >= leftPos + 7 && x < leftPos + 169 && y >= topPos + 45 && y < topPos + 85 && (button == 0 || button == 1)) {
            int index = Math.min(menu.pageCount(), firstPage + (int) (x - leftPos - 7) / 30);
            if (menu.getCarried().isEmpty()) {
                if (index < menu.pageCount()) send(BookBinderMenu.REMOVE_PAGE | index);
            } else send((button == 1 ? BookBinderMenu.INSERT_ONE : BookBinderMenu.INSERT_ALL) | index);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (x >= leftPos + 7 && x < leftPos + 169 && y >= topPos + 45 && y < topPos + 85) {
            firstPage = Math.max(0, Math.min(Math.max(0, menu.pageCount() - 4), firstPage + (amount < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(x, y, amount);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (bookTitle.isFocused() && key != 256) return bookTitle.keyPressed(key, scanCode, modifiers) || bookTitle.canConsumeInput();
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        renderTooltip(pose, mouseX, mouseY);
        if (mouseX >= leftPos + 7 && mouseX < leftPos + 157 && mouseY >= topPos + 45 && mouseY < topPos + 85) {
            int index = firstPage + (mouseX - leftPos - 7) / 30;
            if (index < menu.pageCount()) renderTooltip(pose, menu.page(index), mouseX, mouseY);
        }
        if (mouseX >= leftPos + 27 && mouseX < leftPos + 45 && mouseY >= topPos + 26 && mouseY < topPos + 44
                && (menu.pageCount() == 0 || !BookBinderBlockEntity.isPanel(menu.page(0)))) renderTooltip(pose, Component.translatable("gui.mystcraft.binder.missing_panel"), mouseX, mouseY);
    }
}
