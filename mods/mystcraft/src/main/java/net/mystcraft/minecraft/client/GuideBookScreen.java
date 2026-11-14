package net.mystcraft.minecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.mystcraft.minecraft.guide.GuideContent;
import org.lwjgl.glfw.GLFW;

/** A compact field manual with chapter bookmarks and recipes from the current world. */
public final class GuideBookScreen extends Screen {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("mystcraft", "textures/gui/guide_book.png");
    private static final String PREFIX = "gui.mystcraft.guide.";
    private static final int BOOK_WIDTH = 300, BOOK_HEIGHT = 220;
    private static final int INK = 0x382D22, TEAL = 0x244742, BRASS = 0x9A743B, CREAM = 0xF8EBCB;
    private static final int TEXT_X = 136, TEXT_Y = 76, TEXT_WIDTH = 144, TEXT_LINES = 11, LINE_HEIGHT = 10;
    private static final List<Chapter> CHAPTERS = List.of(
            new Chapter("start", 0), new Chapter("pages", 5), new Chapter("grammar", 10),
            new Chapter("example", 12), new Chapter("modifiers", 18), new Chapter("instability", 21),
            new Chapter("field", 28), new Chapter("recipes", 30), new Chapter("reference", 35));
    private static final List<String> RECIPES = List.of("ink_mixer", "book_binder", "writing_desk", "bookstand", "folder");
    private final List<Component> pages = GuideContent.pages();
    private final IntConsumer rememberPage;
    private int pageIndex, firstLine, left, top, ticks;
    private float scale;
    private List<FormattedCharSequence> bodyLines = List.of();
    private Component pageTitle = Component.empty();
    private ShapedRecipe recipe;
    private Button previous, next, scrollUp, scrollDown;
    private ItemStack hoveredIngredient = ItemStack.EMPTY;

    public GuideBookScreen(int initialPage, IntConsumer rememberPage) {
        super(Component.translatable("item.mystcraft.guide_book"));
        pageIndex = Mth.clamp(initialPage, 0, pages.size() - 1);
        this.rememberPage = rememberPage;
    }

    @Override protected void init() {
        scale = Math.max(0.1F, Math.min(1F, Math.min((width - 8) / (float) BOOK_WIDTH, (height - 8) / (float) BOOK_HEIGHT)));
        left = (width - Math.round(BOOK_WIDTH * scale)) / 2;
        top = (height - Math.round(BOOK_HEIGHT * scale)) / 2;
        for (int i = 0; i < CHAPTERS.size(); i++) {
            int chapterIndex = i;
            Chapter chapter = CHAPTERS.get(i);
            addRenderableWidget(new GuideButton(14, 61 + i * 14, 101, 14,
                    Component.translatable(PREFIX + "chapter." + chapter.key()), null,
                    button -> showPage(chapter.page()), chapterIndex, false));
        }
        addRenderableWidget(new GuideButton(14, 199, 101, 16, Component.translatable("gui.done"), null,
                button -> onClose(), -1, true));
        previous = addRenderableWidget(new GuideButton(134, 199, 24, 16,
                Component.translatable(PREFIX + "previous"), "<", button -> showPage(pageIndex - 1), -1, true));
        next = addRenderableWidget(new GuideButton(264, 199, 24, 16,
                Component.translatable(PREFIX + "next"), ">", button -> showPage(pageIndex + 1), -1, true));
        scrollUp = addRenderableWidget(new GuideButton(282, 73, 9, 10,
                Component.translatable(PREFIX + "scroll_up"), "^", button -> scroll(-1), -1, false));
        scrollDown = addRenderableWidget(new GuideButton(282, 179, 9, 10,
                Component.translatable(PREFIX + "scroll_down"), "v", button -> scroll(1), -1, false));
        layoutPage();
    }

    private void layoutPage() {
        String translated = pages.get(pageIndex).getString();
        int divider = translated.indexOf("\n\n");
        pageTitle = Component.literal(divider < 0 ? translated : translated.substring(0, divider)).withStyle(ChatFormatting.BOLD);
        String body = divider < 0 ? "" : translated.substring(divider + 2);
        recipe = null;
        if (pageIndex >= 30 && pageIndex < 35 && minecraft.level != null) {
            var current = minecraft.level.getRecipeManager().byKey(new ResourceLocation("mystcraft", RECIPES.get(pageIndex - 30)));
            if (current.orElse(null) instanceof ShapedRecipe shaped && shaped.getWidth() <= 3 && shaped.getHeight() <= 3) {
                recipe = shaped;
                // The item diagram replaces the printed grid and its letter legend.
                body = Component.translatable(PREFIX + "recipe_hint").getString();
            }
        }
        bodyLines = font.split(Component.literal(body), TEXT_WIDTH);
        firstLine = Mth.clamp(firstLine, 0, maxScroll());
        updateButtons();
    }

    private int visibleLines() { return recipe == null ? TEXT_LINES : 5; }
    private int maxScroll() { return Math.max(0, bodyLines.size() - visibleLines()); }
    private int chapterIndex() {
        for (int i = CHAPTERS.size() - 1; i >= 0; i--) if (pageIndex >= CHAPTERS.get(i).page()) return i;
        return 0;
    }
    private void showPage(int page) {
        int nextPage = Mth.clamp(page, 0, pages.size() - 1);
        if (nextPage == pageIndex) return;
        pageIndex = nextPage;
        firstLine = 0;
        rememberPage.accept(pageIndex);
        layoutPage();
        triggerImmediateNarration(true);
    }
    private void scroll(int amount) {
        firstLine = Mth.clamp(firstLine + amount, 0, maxScroll());
        updateButtons();
    }
    private void updateButtons() {
        previous.active = pageIndex > 0;
        next.active = pageIndex + 1 < pages.size();
        scrollUp.visible = scrollDown.visible = maxScroll() > 0;
        scrollUp.active = firstLine > 0;
        scrollDown.active = firstLine < maxScroll();
    }

    @Override public void tick() { ticks++; }

    @Override public void render(PoseStack pose, int mouseX, int mouseY, float delta) {
        renderBackground(pose);
        pose.pushPose();
        pose.translate(left, top, 0);
        pose.scale(scale, scale, 1);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, BACKGROUND);
        blit(pose, 0, 0, 0, 0, BOOK_WIDTH, BOOK_HEIGHT, BOOK_WIDTH, BOOK_HEIGHT);
        RenderSystem.disableBlend();
        fittedText(pose, title.copy().withStyle(ChatFormatting.BOLD), 16, 15, 241, 12, TEAL);
        fittedText(pose, Component.translatable(PREFIX + "subtitle"), 16, 30, 240, 9, INK);
        font.draw(pose, Component.translatable(PREFIX + "contents"), 18, 49, BRASS);
        int accent = chapterIndex() == 5 ? 0x884334 : TEAL;
        fittedText(pose, pageTitle, TEXT_X, 48, TEXT_WIDTH, 18, accent);
        fill(pose, TEXT_X, 69, 281, 70, 0xFF000000 | BRASS);
        int bodyY = recipe == null ? TEXT_Y : 138;
        for (int line = firstLine; line < Math.min(bodyLines.size(), firstLine + visibleLines()); line++)
            font.draw(pose, bodyLines.get(line), TEXT_X, bodyY + (line - firstLine) * LINE_HEIGHT, INK);
        if (maxScroll() > 0) {
            int trackTop = recipe == null ? 85 : 138;
            fill(pose, 286, trackTop, 287, 177, 0xFFCCB992);
            int thumbY = trackTop + (177 - trackTop - 8) * firstLine / maxScroll();
            fill(pose, 285, thumbY, 288, thumbY + 8, 0xFF000000 | BRASS);
        }
        Component counter = Component.translatable(PREFIX + "page", pageIndex + 1, pages.size());
        fittedText(pose, counter, 166, 203, 90, 9, CREAM);
        // A fine brass rule makes the reader's progress visible without obscuring the page.
        fill(pose, 136, 193, 281, 194, 0xFFCCB992);
        fill(pose, 136, 193, 136 + 145 * (pageIndex + 1) / pages.size(), 194, 0xFF000000 | BRASS);
        pose.popPose();
        hoveredIngredient = ItemStack.EMPTY;
        if (recipe != null) renderRecipe(pose, mouseX, mouseY);
        super.render(pose, mouseX, mouseY, delta);
        if (!hoveredIngredient.isEmpty()) renderTooltip(pose, hoveredIngredient, mouseX, mouseY);
    }

    private void fittedText(PoseStack pose, Component text, int x, int y, int width, int height, int color) {
        List<FormattedCharSequence> lines = font.split(text, width);
        float textScale = Math.min(1F, height / (float) Math.max(1, lines.size() * font.lineHeight));
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(textScale, textScale, 1);
        for (int i = 0; i < lines.size(); i++) font.draw(pose, lines.get(i), 0, i * font.lineHeight, color);
        pose.popPose();
    }

    private void renderRecipe(PoseStack pose, int mouseX, int mouseY) {
        pose.pushPose();
        pose.translate(left, top, 0);
        pose.scale(scale, scale, 1);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++)
            recipeSlot(pose, 146 + column * 18, 76 + row * 18);
        recipeSlot(pose, 253, 94);
        font.draw(pose, "->", 217, 99, BRASS);
        pose.popPose();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.translate(left, top, 0);
        modelView.scale(scale, scale, 1);
        RenderSystem.applyModelViewMatrix();
        for (int row = 0; row < recipe.getHeight(); row++) {
            for (int column = 0; column < recipe.getWidth(); column++) {
                ItemStack[] alternatives = recipe.getIngredients().get(row * recipe.getWidth() + column).getItems();
                if (alternatives.length == 0) continue;
                ItemStack ingredient = alternatives[(ticks / 30) % alternatives.length];
                recipeItem(ingredient, 147 + column * 18, 77 + row * 18, mouseX, mouseY);
            }
        }
        recipeItem(recipe.getResultItem(), 254, 95, mouseX, mouseY);
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
    }
    private void recipeSlot(PoseStack pose, int x, int y) {
        fill(pose, x, y, x + 18, y + 18, 0xFF9B8460);
        fill(pose, x + 1, y + 1, x + 18, y + 18, 0xFFFFF3D9);
        fill(pose, x + 1, y + 1, x + 17, y + 17, 0xFFDCCAA5);
    }
    private void recipeItem(ItemStack item, int x, int y, int mouseX, int mouseY) {
        minecraft.getItemRenderer().renderAndDecorateItem(item, x, y);
        minecraft.getItemRenderer().renderGuiItemDecorations(font, item, x, y);
        if (inside(mouseX, mouseY, x, y, 16, 16)) hoveredIngredient = item;
    }
    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        double logicalX = (mouseX - left) / scale, logicalY = (mouseY - top) / scale;
        return logicalX >= x && logicalX < x + width && logicalY >= y && logicalY < y + height;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (maxScroll() > 0 && inside(mouseX, mouseY, 130, 72, 162, 119)) {
            scroll(amount > 0 ? -3 : 3);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) { showPage(pageIndex - 1); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) { showPage(pageIndex + 1); return true; }
        if (key == GLFW.GLFW_KEY_HOME) { showPage(0); return true; }
        if (key == GLFW.GLFW_KEY_END) { showPage(pages.size() - 1); return true; }
        if (key == GLFW.GLFW_KEY_UP && maxScroll() > 0) { scroll(-1); return true; }
        if (key == GLFW.GLFW_KEY_DOWN && maxScroll() > 0) { scroll(1); return true; }
        if (minecraft.options.keyInventory.matches(key, scanCode)) { onClose(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public Component getNarrationMessage() {
        return Component.empty().append(title).append(". ")
                .append(Component.translatable(PREFIX + "page", pageIndex + 1, pages.size())).append(". ")
                .append(pages.get(pageIndex)).append(". ").append(Component.translatable(PREFIX + "navigation"));
    }
    @Override public boolean isPauseScreen() { return true; }

    private record Chapter(String key, int page) {}

    private final class GuideButton extends Button {
        private final int logicalX, logicalY, logicalWidth, logicalHeight, chapter;
        private final String symbol;
        private final boolean dark;

        private GuideButton(int x, int y, int width, int height, Component label, String symbol,
                OnPress action, int chapter, boolean dark) {
            super(left + Math.round(x * scale), top + Math.round(y * scale), Math.round(width * scale),
                    Math.round(height * scale), label, action);
            logicalX = x;
            logicalY = y;
            logicalWidth = width;
            logicalHeight = height;
            this.symbol = symbol;
            this.chapter = chapter;
            this.dark = dark;
        }

        @Override public void renderButton(PoseStack pose, int mouseX, int mouseY, float delta) {
            boolean selected = chapter >= 0 && chapter == chapterIndex();
            boolean highlighted = isHoveredOrFocused() && active;
            pose.pushPose();
            pose.translate(left, top, 0);
            pose.scale(scale, scale, 1);
            int textColor = dark || selected ? CREAM : TEAL;
            if (!active) textColor = dark ? 0x8D9A87 : 0xB3A58A;
            int background = selected ? 0xFF244742 : highlighted ? (dark ? 0xFF365B53 : 0xFFDCC89E) : 0;
            if (background != 0) fill(pose, logicalX, logicalY, logicalX + logicalWidth, logicalY + logicalHeight, background);
            if (selected || highlighted) fill(pose, logicalX, logicalY, logicalX + 2, logicalY + logicalHeight, 0xFFBB9855);
            String label = symbol == null ? getMessage().getString() : symbol;
            int available = logicalWidth - 10;
            if (symbol == null && font.width(label) > available) label = font.plainSubstrByWidth(label, available - font.width("...")) + "...";
            int labelX = chapter >= 0 ? logicalX + 6 : logicalX + (logicalWidth - font.width(label)) / 2;
            font.draw(pose, label, labelX, logicalY + (logicalHeight - 8) / 2, textColor);
            pose.popPose();
            if (highlighted && (symbol != null || font.width(getMessage()) > available))
                renderTooltip(pose, getMessage(), mouseX, mouseY);
        }
    }
}
