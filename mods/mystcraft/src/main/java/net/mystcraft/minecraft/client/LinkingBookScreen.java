package net.mystcraft.minecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.mystcraft.integration.network.LinkingNetwork;
import net.mystcraft.minecraft.age.AgeBookData;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.writing.SymbolRegistry;

/** Classic cover, ordered page browsing and original glyphs. The link panel remains a static gradient. */
public final class LinkingBookScreen extends Screen {
    private static final ResourceLocation COVER = texture("bookui_cover.png");
    private static final ResourceLocation PAGE = texture("bookui_pager.png");
    private static final ResourceLocation LEFT_PAGE = texture("bookui_pagel.png");
    private static final ResourceLocation FULL_PAGE = texture("bookui_rpage_full.png");
    private final LinkingNetwork.OpenBook book;
    private final net.mystcraft.minecraft.exploration.BookDisplayMenu displayMenu;
    private final net.minecraft.world.item.ItemStack displaySnapshot;
    private final List<CompoundTag> pages;
    private final boolean descriptive;
    private final List<String> authors;
    private int left;
    private int top;
    private float scale;
    private int pageIndex;
    private boolean displayRefreshRequested;
    private Button panel;
    private Button previous;
    private Button next;
    private final ClassicDisarmPanelEffect disarmEffect = new ClassicDisarmPanelEffect();

    public LinkingBookScreen(LinkingNetwork.OpenBook book) { this(book, null); }
    public LinkingBookScreen(LinkingNetwork.OpenBook book, net.mystcraft.minecraft.exploration.BookDisplayMenu displayMenu) {
        super(book.title());
        this.displayMenu = displayMenu;
        this.displaySnapshot = displayMenu == null ? net.minecraft.world.item.ItemStack.EMPTY : displayMenu.displayed().copy();
        this.book = book;
        var contents = book.contents();
        descriptive = AgeBookData.isDescriptive(contents);
        List<CompoundTag> stored = contents.hasTag() ? PageData.readPages(contents.getTag()) : List.of();
        if (stored.isEmpty()) {
            CompoundTag linkPanel = new CompoundTag();
            PageData.makeLinkPanel(linkPanel);
            stored = List.of(linkPanel);
        }
        pages = List.copyOf(stored.subList(0, Math.min(AgeBookData.MAX_PAGES, stored.size())));
        var names = contents.hasTag() ? contents.getTag().getList("Authors", Tag.TAG_STRING) : new net.minecraft.nbt.ListTag();
        var authorList = new java.util.ArrayList<String>();
        for (int i = 0; i < Math.min(16, names.size()); i++) authorList.add(names.getString(i));
        if (authorList.isEmpty() && contents.hasTag() && contents.getTag().contains("Author", Tag.TAG_STRING))
            authorList.add(contents.getTag().getString("Author"));
        authors = List.copyOf(authorList);
    }

    private static ResourceLocation texture(String name) { return new ResourceLocation("mystcraft", "gui/" + name); }

    @Override protected void init() {
        scale = Math.max(0.1F, Math.min(1.0F, Math.min((width - 8) / 327.0F, (height - 8) / 199.0F)));
        left = (width - (int) (327 * scale)) / 2;
        top = (height - (int) (199 * scale)) / 2;
        panel = addRenderableWidget(new Button(sx(173), sy(20), sw(132), sw(83),
                Component.translatable("gui.mystcraft.link"), button -> {
                    button.active = false;
                    LinkingNetwork.requestLink(book.token());
                    onClose();
                }) {
            @Override public void renderButton(PoseStack pose, int mouseX, int mouseY, float delta) {
                if (isHoveredOrFocused()) {
                    fill(pose, x, y, x + width, y + 1, 0xFFFFFFAA);
                    fill(pose, x, y + height - 1, x + width, y + height, 0xFFFFFFAA);
                    fill(pose, x, y, x + 1, y + height, 0xFFFFFFAA);
                    fill(pose, x + width - 1, y, x + width, y + height, 0xFFFFFFAA);
                }
            }
        });
        previous = addRenderableWidget(pageButton(16, "gui.mystcraft.previous_page", "<", -1));
        next = addRenderableWidget(pageButton(294, "gui.mystcraft.next_page", ">", 1));
        updateButtons();
    }

    private Button pageButton(int x, String narration, String arrow, int direction) {
        return new Button(sx(x), sy(173), sw(16), sw(12), Component.translatable(narration), b -> turn(direction)) {
            @Override public void renderButton(PoseStack pose, int mouseX, int mouseY, float delta) {
                drawCenteredString(pose, font, arrow, this.x + this.width / 2, this.y + 1,
                        isHoveredOrFocused() ? 0x9A611D : 0x48351E);
            }
        };
    }
    private int sx(int x) { return left + Math.round(x * scale); }
    private int sy(int y) { return top + Math.round(y * scale); }
    private int sw(int value) { return Math.max(1, Math.round(value * scale)); }
    private void turn(int direction) {
        pageIndex = Math.max(0, Math.min(pages.size(), pageIndex + direction));
        updateButtons();
    }
    private void updateButtons() {
        if (panel == null) return;
        panel.visible = pageIndex == 0;
        panel.active = pageIndex == 0 && book.available();
        previous.visible = previous.active = pageIndex > 0;
        next.visible = next.active = pageIndex < pages.size();
    }

    @Override public void render(PoseStack pose, int mouseX, int mouseY, float delta) {
        if (displayMenu != null && minecraft.player != null) {
            if (minecraft.player.containerMenu != displayMenu) { onClose(); return; }
            if (!(displayMenu.displayed().getItem() instanceof net.mystcraft.minecraft.item.LinkingBookItem)) {
                minecraft.setScreen(new BookDisplayScreen(displayMenu, minecraft.player.getInventory(), Component.translatable("container.mystcraft.book_display")));
                return;
            }
            if (!net.minecraft.world.item.ItemStack.matches(displayMenu.displayed(), displaySnapshot)) {
                if (panel != null) panel.active = false;
                if (!displayRefreshRequested) {
                    displayRefreshRequested = true;
                    net.mystcraft.integration.network.MenuActions.send(displayMenu.containerId, net.mystcraft.minecraft.exploration.BookDisplayMenu.READ);
                }
            }
        }
        renderBackground(pose);
        pose.pushPose();
        pose.translate(left, top, 0);
        pose.scale(scale, scale, 1);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, COVER);
        blit(pose, 0, 7, 152, 0, 34, 192);
        blit(pose, 34, 7, 49, 0, 103, 192);
        blit(pose, 137, 7, 45, 0, 4, 192);
        blit(pose, 141, 7, 0, 0, 186, 192);
        if (descriptive) {
            blit(pose, 0, 7, 186, 0, 34, 192);
            blit(pose, 293, 7, 186, 0, 34, 192);
        }
        if (pageIndex > 0) drawPage(pose, LEFT_PAGE, 7);
        SymbolRegistry.Definition symbol = null;
        if (pageIndex == 0) {
            fillGradient(pose, 173, 20, 305, 103,
                    book.visited() ? 0xFF000044 : 0xFF000000,
                    book.visited() ? 0xFF006666 : 0xFF000000);
            if (net.mystcraft.minecraft.linking.LinkModifiers.from(book.contents()).disarm())
                disarmEffect.render(pose, 173, 20, 132, 83);
            drawPage(pose, PAGE, 163);
            drawCoverText(pose);
            drawDestination(pose);
        } else if (pageIndex < pages.size()) {
            drawPage(pose, FULL_PAGE, 163);
            symbol = PageData.legacySymbol(pages.get(pageIndex)).flatMap(SymbolRegistry::find).orElse(null);
            var page = new net.minecraft.world.item.ItemStack(net.mystcraft.integration.registry.AgeRegistry.PAGE.get());
            PageData.applyPayload(page, pages.get(pageIndex));
            if (!DynamicPageRenderer.draw(pose, page, 171, 25, 140, 140)
                    && PageData.legacySymbol(pages.get(pageIndex)).isPresent()) ClassicBookGlyphs.draw(pose, symbol, 171, 25, 140);
        }
        String counter = pageIndex + "/" + pages.size();
        font.draw(pose, counter, 165 - font.width(counter) / 2F, 185, 0x000000);
        pose.popPose();
        super.render(pose, mouseX, mouseY, delta);
        if (displayMenu != null && pageIndex == 0) {
            minecraft.getItemRenderer().renderAndDecorateItem(displayMenu.displayed(), sx(41), sy(21));
            var carried = displayMenu.getCarried();
            if (!carried.isEmpty()) minecraft.getItemRenderer().renderAndDecorateItem(carried, mouseX - 8, mouseY - 8);
            if (inside(mouseX, mouseY, 41, 21, 16, 16)) renderTooltip(pose, displayMenu.displayed(), mouseX, mouseY);
        }
        if (pageIndex > 0 && pageIndex < pages.size() && inside(mouseX, mouseY, 171, 25, 140, 140)) {
            var token = PageData.legacySymbol(pages.get(pageIndex));
            if (token.isPresent()) renderTooltip(pose, Component.literal(symbol == null ? token.get() : symbol.displayName()), mouseX, mouseY);
        }
    }

    private void drawPage(PoseStack pose, ResourceLocation texture, int x) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);
        blit(pose, x, 0, 0, 0, 156, 195);
        RenderSystem.disableBlend();
    }
    private void drawCoverText(PoseStack pose) {
        int y = 40;
        for (var line : font.split(title, 102)) {
            if (y > 67) break;
            font.draw(pose, line, 40, y, 0x000000);
            y += font.lineHeight;
        }
        pose.pushPose();
        pose.translate(50, y + 1, 0);
        pose.scale(0.5F, 0.5F, 1);
        for (int i = 0; i < authors.size(); i++) font.draw(pose, font.plainSubstrByWidth(authors.get(i), 170), 0, i * 10, 0x000000);
        pose.popPose();
    }
    private void drawDestination(PoseStack pose) {
        font.draw(pose, Component.translatable("gui.mystcraft.destination"), 174, 116, 0x302719);
        int lineY = 130;
        for (var line : font.split(book.newAge() ? Component.translatable("gui.mystcraft.new_age") : Component.literal(book.dimension()), 126)) {
            if (lineY > 148) break;
            font.draw(pose, line, 174, lineY, 0x302719);
            lineY += font.lineHeight;
        }
        if (!book.newAge()) font.draw(pose, String.format(Locale.ROOT, "%.0f, %.0f, %.0f", book.x(), book.y(), book.z()), 174, 157, 0x302719);
        font.draw(pose, Component.translatable(book.available() ? "gui.mystcraft.click_panel" : "gui.mystcraft.unavailable"), 174, 174, 0x302719);
    }
    private boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        double localX = (mouseX - left) / scale, localY = (mouseY - top) / scale;
        return localX >= x && localX <= x + w && localY >= y && localY <= y + h;
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (displayMenu != null && pageIndex == 0 && button >= 0 && button <= 1 && inside(mouseX, mouseY, 41, 21, 16, 16)) {
            if (minecraft.gameMode != null && minecraft.player != null)
                minecraft.gameMode.handleInventoryMouseClick(displayMenu.containerId, 0, button,
                        hasShiftDown() ? net.minecraft.world.inventory.ClickType.QUICK_MOVE : net.minecraft.world.inventory.ClickType.PICKUP, minecraft.player);
            return true;
        }
        if (button == 0 && pageIndex == 0 && inside(mouseX, mouseY, 173, 20, 132, 83)) {
            if (panel.active) return super.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && inside(mouseX, mouseY, 0, 0, 156, 195)) { turn(-1); return true; }
        if (button == 0 && inside(mouseX, mouseY, 158, 0, 154, 195)) { turn(1); return true; }
        return false;
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) { onClose(); return true; }
        if (keyCode == 263 || minecraft.options.keyLeft.matches(keyCode, scanCode)) { turn(-1); return true; }
        if (keyCode == 262 || minecraft.options.keyRight.matches(keyCode, scanCode)) { turn(1); return true; }
        if (keyCode == 268) { pageIndex = 0; updateButtons(); return true; }
        if (keyCode == 269) { pageIndex = pages.size(); updateButtons(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public void onClose() {
        if (displayMenu != null && minecraft.player != null && minecraft.player.containerMenu == displayMenu) minecraft.player.closeContainer();
        else super.onClose();
    }
    @Override public boolean isPauseScreen() { return false; }
}
