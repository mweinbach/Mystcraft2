package ports.jurassicraft.journal.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.journal.JournalType;
import ports.jurassicraft.journal.JournalContent;

/** Two-page reader for the original resource-backed journal entries. */
@OnlyIn(Dist.CLIENT)
public final class JournalScreen extends Screen {
    private static final ResourceLocation BACKGROUND = new ResourceLocation(PortMod.MOD_ID, "textures/journal/background.png");
    private static final ResourceLocation WIDGETS = new ResourceLocation(PortMod.MOD_ID, "textures/journal/widgets.png");
    private static final int WIDTH = 94, LINES_PER_PAGE = 13, LINE_HEIGHT = 11;
    private final JournalType type;
    private final List<List<FormattedCharSequence>> pages = new ArrayList<>();
    private int spread;
    private Button previous, next;

    public JournalScreen(JournalType type) {
        super(Component.translatable("item.jurassicraft.ingen_journal"));
        this.type = type;
    }
    @Override protected void init() {
        loadPages();
        spread = Math.min(spread, spreadCount() - 1);
        int x = (width - 256) / 2, y = (height - 192) / 2;
        previous = addRenderableWidget(new PageButton(x - 3, y + 180, false, b -> changeSpread(-1)));
        next = addRenderableWidget(new PageButton(x + 235, y + 180, true, b -> changeSpread(1)));
        updateButtons();
    }
    private int spreadCount() { return Math.max(1, (pages.size() + 1) / 2); }
    private void updateButtons() { previous.visible = spread > 0; next.visible = spread + 1 < spreadCount(); }
    private void changeSpread(int delta) {
        spread = Math.max(0, Math.min(spreadCount() - 1, spread + delta));
        updateButtons(); triggerImmediateNarration(true);
    }
    private void loadPages() {
        pages.clear();
        ResourceLocation resource = new ResourceLocation(PortMod.MOD_ID, "journal_entries/" + type.id() + ".json");
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(resource)) {
            for (List<String> entry : JournalContent.read(reader)) {
                List<FormattedCharSequence> page = new ArrayList<>();
                for (String element : entry) {
                    List<FormattedCharSequence> lines = font.split(JournalContent.formatLine(element), WIDTH);
                    if (lines.isEmpty()) lines = List.of(FormattedCharSequence.EMPTY);
                    for (FormattedCharSequence line : lines) {
                        if (page.size() == LINES_PER_PAGE) { pages.add(page); page = new ArrayList<>(); }
                        page.add(line);
                    }
                }
                // An entry is an intentional page break, including an empty entry.
                pages.add(page);
            }
        } catch (IOException | RuntimeException ex) {
            pages.clear();
            pages.add(font.split(Component.translatable("gui.jurassicraft.journal.load_error"), WIDTH));
        }
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) { changeSpread(-1); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) { changeSpread(1); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public Component getNarrationMessage() {
        MutableComponent message = Component.empty().append(title).append(". ").append(Component.translatable("journal.jurassicraft." + type.id()));
        for (int i = spread * 2; i < Math.min(spread * 2 + 2, pages.size()); i++) {
            for (FormattedCharSequence line : pages.get(i)) {
                StringBuilder text = new StringBuilder();
                line.accept((index, style, codepoint) -> { text.appendCodePoint(codepoint); return true; });
                message.append(". ").append(text.toString());
            }
        }
        return message;
    }
    @Override public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        int x = (width - 256) / 2, y = (height - 192) / 2;
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, BACKGROUND);
        blit(pose, x, y, 0, 0, 256, 192);
        drawPage(pose, spread * 2, x + 18, y + 16);
        drawPage(pose, spread * 2 + 1, x + 145, y + 16);
        super.render(pose, mouseX, mouseY, partialTick);
    }
    private void drawPage(PoseStack pose, int pageIndex, int x, int y) {
        if (pageIndex >= pages.size()) return;
        List<FormattedCharSequence> page = pages.get(pageIndex);
        for (int i = 0; i < page.size(); i++) font.draw(pose, page.get(i), x, y + i * LINE_HEIGHT, 0);
    }
    @Override public boolean isPauseScreen() { return false; }

    private static final class PageButton extends Button {
        private final boolean forward;
        PageButton(int x, int y, boolean forward, OnPress action) {
            super(x, y, 23, 13, Component.translatable(forward ? "gui.jurassicraft.journal.next" : "gui.jurassicraft.journal.previous"), action);
            this.forward = forward;
        }
        @Override public void renderButton(PoseStack pose, int mouseX, int mouseY, float tick) {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderTexture(0, WIDGETS);
            blit(pose, x, y, isHoveredOrFocused() ? 23 : 0, forward ? 194 : 207, 23, 13);
        }
    }
}
