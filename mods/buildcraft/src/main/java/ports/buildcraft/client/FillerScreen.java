package ports.buildcraft.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.FillerBlockEntity;
import ports.buildcraft.builders.FillerMenu;
import ports.buildcraft.builders.filler.FillerParameter;
import ports.buildcraft.builders.filler.FillerPatterns;
import ports.buildcraft.builders.filler.Pattern;
import ports.buildcraft.builders.filler.PatternStatement;

/**
 * Upstream {@code GuiFiller} as laid out by {@code filler.json}: the pattern and its parameters, the
 * excavate and invert toggles, the patterns to drag in on the left and the progress ledger on the right.
 * Pressing on the pattern or a parameter opens upstream's ring of choices; releasing over one picks it.
 */
public final class FillerScreen extends BCScreen<FillerMenu> {
    private static final ResourceLocation MISC_SLOTS = BuildCraft.id("textures/gui/misc_slots.png");
    private static final ResourceLocation LEDGER = BuildCraft.id("textures/gui/ledger_right.png");
    private static final ResourceLocation LOCK = BuildCraft.id("textures/gui/lock.png");
    private static final int PATTERN_X = 12, PATTERN_Y = 32, PATTERN_SIZE = 32;
    private static final int PARAM_X = 53, PARAM_Y = 39;
    private static final int EXCAVATE_X = 130, INVERT_X = 152, BUTTON_Y = 40;
    private static final int LOCK_X = 12, LOCK_Y = 16;
    /** Upstream {@code GuiElementStatementVariant.OFFSET_HOVER}: the centre, then rings of 8 and 16 around it. */
    private static final int[][] OFFSET_HOVER = {
            {0, 0},
            {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0},
            {-2, -2}, {-1, -2}, {0, -2}, {1, -2}, {2, -2}, {2, -1}, {2, 0}, {2, 1},
            {2, 2}, {1, 2}, {0, 2}, {-1, 2}, {-2, 2}, {-2, 1}, {-2, 0}, {-2, -1}};
    private static final int LEDGER_COLOUR = 0xFF6CD41F;
    private static final int LEDGER_TITLE_COLOUR = 0xFFE1C92F;
    private static final int LEDGER_GAP = 4;
    private static final int LEDGER_CLOSED_WIDTH = 2 + 16 + LEDGER_GAP;
    private static final int LEDGER_CLOSED_HEIGHT = LEDGER_GAP + 16 + LEDGER_GAP;
    private static final int SLOT = 18;
    private static boolean ledgerOpen;

    private double ledgerWidth = LEDGER_CLOSED_WIDTH, ledgerHeight = LEDGER_CLOSED_HEIGHT;
    private double lastLedgerWidth = ledgerWidth, lastLedgerHeight = ledgerHeight;
    @Nullable private Variant variant;
    private boolean dragging;
    @Nullable private Object dragged;

    /** Upstream's pop-up of choices, positioned from the top-left of the slot it opened over. */
    private record Variant(int x, int y, int size, Object[] options, int[][] positions, Predicate<Object> canSet, Selector select) {}

    @FunctionalInterface
    private interface Selector {
        void select(Object option);
    }

    public FillerScreen(FillerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BuildCraft.id("textures/gui/filler.png"), 176, 241);
        if (ledgerOpen) {
            ledgerWidth = lastLedgerWidth = ledgerMaxWidth();
            ledgerHeight = lastLedgerHeight = ledgerMaxHeight();
        }
    }

    @Nullable
    private FillerBlockEntity filler() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos()) instanceof FillerBlockEntity filler ? filler : null;
    }

    private void press(int button) {
        if (minecraft == null || minecraft.gameMode == null) return;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private void clickSound() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // Layout

    private static boolean in(double mouseX, double mouseY, double x, double y, double width, double height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    private int ledgerX() {
        return leftPos + imageWidth;
    }

    private int ledgerY() {
        return topPos + 5;
    }

    private int ledgerMaxWidth() {
        int textWidth = font.width(Component.translatable("gui.progress"));
        FillerBlockEntity filler = filler();
        if (filler != null) {
            textWidth = Math.max(textWidth, 20 + font.width(Integer.toString(filler.getCountToBreak())));
            textWidth = Math.max(textWidth, 20 + font.width(Integer.toString(filler.getCountToPlace())));
        }
        return Math.max(LEDGER_CLOSED_WIDTH, 2 + 16 + LEDGER_GAP + textWidth) + LEDGER_GAP * 2;
    }

    private int ledgerMaxHeight() {
        return LEDGER_GAP + 14 + 16 + 2 + 16 + LEDGER_GAP * 2;
    }

    private int sourceWidth() {
        int width = 0;
        for (List<Pattern> group : FillerPatterns.GROUPS) width = Math.max(width, group.size());
        return Math.min(4, width) * SLOT;
    }

    private int sourceHeight() {
        int rows = 0;
        for (List<Pattern> group : FillerPatterns.GROUPS) rows += (group.size() + 3) / 4;
        return rows * SLOT + 4;
    }

    @FunctionalInterface
    private interface SourceSlot {
        void visit(Pattern pattern, int x, int y);
    }

    /** Upstream {@code GuiElementStatementSource} on the left: rows of four, filled from the right, a new row per group. */
    private void forEachSource(SourceSlot visitor) {
        int originX = leftPos - sourceWidth();
        int originY = topPos + 5;
        int column = 3;
        int row = 0;
        for (List<Pattern> group : FillerPatterns.GROUPS) {
            for (Pattern pattern : group) {
                visitor.visit(pattern, originX + column * SLOT, originY + row * SLOT);
                column--;
                if (column == -1) {
                    column = 3;
                    row++;
                }
            }
            if (!group.isEmpty() && column != 3) {
                column = 3;
                row++;
            }
        }
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        if (in(mouseX, mouseY, leftPos - sourceWidth(), topPos + 5, sourceWidth(), sourceHeight())) return false;
        if (in(mouseX, mouseY, ledgerX(), ledgerY(), ledgerWidth, ledgerHeight)) return false;
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    // Ticking

    @Override
    protected void containerTick() {
        super.containerTick();
        lastLedgerWidth = ledgerWidth;
        lastLedgerHeight = ledgerHeight;
        double maxWidth = ledgerMaxWidth(), maxHeight = ledgerMaxHeight();
        double targetWidth = ledgerOpen ? maxWidth : LEDGER_CLOSED_WIDTH;
        double targetHeight = ledgerOpen ? maxHeight : LEDGER_CLOSED_HEIGHT;
        double step = Mth.clamp(Math.max(maxWidth - LEDGER_CLOSED_WIDTH, maxHeight - LEDGER_CLOSED_HEIGHT) / 5, 1, 15);
        ledgerWidth = approach(ledgerWidth, targetWidth, step);
        ledgerHeight = approach(ledgerHeight, targetHeight, step);
    }

    private static double approach(double current, double target, double step) {
        return current < target ? Math.min(target, current + step) : Math.max(target, current - step);
    }

    // Drawing

    private void drawSlot(PoseStack poseStack, @Nullable Object slot, int x, int y) {
        RenderSystem.setShaderTexture(0, MISC_SLOTS);
        blit(poseStack, x, y, 0, 0, SLOT, SLOT);
        ResourceLocation sprite = spriteOf(slot);
        if (sprite != null) {
            RenderSystem.setShaderTexture(0, sprite);
            blit(poseStack, x + 1, y + 1, 16, 16, 0, 0, 16, 16, 16, 16);
        }
    }

    @Nullable
    private static ResourceLocation spriteOf(@Nullable Object slot) {
        if (slot instanceof Pattern pattern) return pattern.sprite();
        if (slot instanceof FillerParameter parameter) return parameter.sprite();
        return null;
    }

    @Nullable
    private static Component tooltipOf(@Nullable Object slot) {
        if (slot instanceof Pattern pattern) return Component.translatable(pattern.translationKey());
        if (slot instanceof FillerParameter parameter) return Component.translatable(parameter.translationKey());
        return null;
    }

    /** Upstream {@code SpriteNineSliced}: corners stay their size, edges and centre stretch. */
    private void nineSlice(PoseStack poseStack, int x, int y, int width, int height, int u, int v, int size, int border, int textureSize) {
        int inner = size - border * 2;
        int[] xs = {x, x + border, x + width - border, x + width};
        int[] ys = {y, y + border, y + height - border, y + height};
        int[] us = {u, u + border, u + border + inner};
        int[] uw = {border, inner, border};
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                int w = xs[col + 1] - xs[col];
                int h = ys[row + 1] - ys[row];
                if (w <= 0 || h <= 0) continue;
                blit(poseStack, xs[col], ys[row], w, h, us[col], v + us[row] - u, uw[col], uw[row], textureSize, textureSize);
            }
        }
    }

    @Override
    protected void renderExtras(PoseStack poseStack, float partialTicks) {
        FillerBlockEntity filler = filler();
        if (filler == null) return;
        PatternStatement statement = filler.patternStatement;
        Pattern pattern = statement.get();
        RenderSystem.setShaderTexture(0, pattern.sprite());
        blit(poseStack, leftPos + PATTERN_X, topPos + PATTERN_Y, PATTERN_SIZE, PATTERN_SIZE, 0, 0, 16, 16, 16, 16);
        for (int i = 0; i < PatternStatement.MAX_PARAMS; i++) {
            int x = leftPos + PARAM_X + SLOT * i, y = topPos + PARAM_Y;
            if (i >= pattern.maxParameters()) {
                RenderSystem.setShaderTexture(0, MISC_SLOTS);
                blit(poseStack, x, y, 0, 0, SLOT, SLOT);
                blit(poseStack, x, y, SLOT, 0, SLOT, SLOT);
            } else {
                drawSlot(poseStack, statement.get(i), x, y);
            }
        }
        RenderSystem.setShaderTexture(0, texture);
        drawButton(poseStack, EXCAVATE_X, 192, filler.canExcavateSetting());
        drawButton(poseStack, INVERT_X, 224, filler.isInverted());
        if (filler.isLocked()) {
            RenderSystem.setShaderTexture(0, LOCK);
            blit(poseStack, leftPos + LOCK_X, topPos + LOCK_Y, 16, 16, 0, 0, 16, 16, 16, 16);
        }
        forEachSource((source, x, y) -> drawSlot(poseStack, source, x, y));
        renderLedger(poseStack, filler, partialTicks);
    }

    /** Upstream's four-mode button: enabled, hovered, active and active-hovered quarters. */
    private void drawButton(PoseStack poseStack, int x, int uStart, boolean active) {
        boolean hovered = in(lastMouseX, lastMouseY, leftPos + x, topPos + BUTTON_Y, 16, 16);
        blit(poseStack, leftPos + x, topPos + BUTTON_Y, uStart + (active ? 16 : 0), hovered ? 16 : 0, 16, 16);
    }

    private double lastMouseX, lastMouseY;

    private void renderLedger(PoseStack poseStack, FillerBlockEntity filler, float partialTicks) {
        int width = (int) Math.round(lastLedgerWidth + (ledgerWidth - lastLedgerWidth) * partialTicks);
        int height = (int) Math.round(lastLedgerHeight + (ledgerHeight - lastLedgerHeight) * partialTicks);
        int x = ledgerX(), y = ledgerY();
        RenderSystem.setShaderTexture(0, LEDGER);
        RenderSystem.setShaderColor((LEDGER_COLOUR >> 16 & 0xFF) / 255F, (LEDGER_COLOUR >> 8 & 0xFF) / 255F, (LEDGER_COLOUR & 0xFF) / 255F, 1);
        nineSlice(poseStack, x, y, width, height, 0, 0, 16, 4, 16);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        int iconX = x + 2, iconY = y + LEDGER_GAP;
        scissor(iconX, iconY, width - 4, height - 8);
        itemRenderer.renderGuiItem(new ItemStack(Items.IRON_INGOT), iconX, iconY);
        if (width > LEDGER_CLOSED_WIDTH || height > LEDGER_CLOSED_HEIGHT) {
            int innerX = iconX + 16 + LEDGER_GAP;
            font.drawShadow(poseStack, Component.translatable("gui.progress"), innerX, iconY + 3, LEDGER_TITLE_COLOUR);
            itemRenderer.renderGuiItem(new ItemStack(Items.IRON_PICKAXE), innerX, iconY + 14);
            font.draw(poseStack, Integer.toString(filler.getCountToBreak()), innerX + 20, iconY + 14 + 5, 0xFF333333);
            itemRenderer.renderGuiItem(new ItemStack(Blocks.BRICKS), innerX, iconY + 32);
            font.draw(poseStack, Integer.toString(filler.getCountToPlace()), innerX + 20, iconY + 32 + 5, 0xFF333333);
        }
        RenderSystem.disableScissor();
    }

    private void scissor(int x, int y, int width, int height) {
        Window window = minecraft.getWindow();
        double scale = window.getGuiScale();
        RenderSystem.enableScissor((int) (x * scale), (int) (window.getHeight() - (y + height) * scale),
                Math.max(0, (int) (width * scale)), Math.max(0, (int) (height * scale)));
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        font.draw(poseStack, title, (imageWidth - font.width(title)) / 2F, 10, 0x404040);
        font.draw(poseStack, Component.translatable("gui.filling.resources"), 7, 74, 0x404040);
        font.draw(poseStack, playerInventoryTitle, 7, 141, 0x404040);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        super.render(poseStack, mouseX, mouseY, partialTicks);
        poseStack.pushPose();
        poseStack.translate(0, 0, 400);
        if (variant != null) renderVariant(poseStack);
        if (dragging) renderDragged(poseStack, mouseX, mouseY);
        poseStack.popPose();
        if (variant != null) renderVariantTooltip(poseStack, mouseX, mouseY);
        else if (!dragging && menu.getCarried().isEmpty()) renderWidgetTooltip(poseStack, mouseX, mouseY);
    }

    private void renderVariantTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        for (int i = 0; i < variant.options().length; i++) {
            Component tooltip = tooltipOf(variant.options()[i]);
            if (tooltip != null && in(mouseX, mouseY, variant.positions()[i][0], variant.positions()[i][1], SLOT, SLOT)) {
                renderTooltip(poseStack, tooltip, mouseX, mouseY);
            }
        }
    }

    private void renderVariant(PoseStack poseStack) {
        RenderSystem.setShaderTexture(0, MISC_SLOTS);
        nineSlice(poseStack, variant.x() + offsetOf(variant), variant.y() + offsetOf(variant), variant.size(), variant.size(), 18, 18, 36, 8, 256);
        for (int i = 0; i < variant.options().length; i++) {
            if (variant.options()[i] != null) drawSlot(poseStack, variant.options()[i], variant.positions()[i][0], variant.positions()[i][1]);
        }
    }

    private static int offsetOf(Variant variant) {
        return -SLOT * (variant.options().length > 9 ? 2 : 1) - 4;
    }

    private void renderDragged(PoseStack poseStack, int mouseX, int mouseY) {
        if (!canDropAt(mouseX, mouseY)) RenderSystem.setShaderColor(1.0F, 0.7F, 0.7F, 1);
        drawSlot(poseStack, dragged, mouseX - 9, mouseY - 9);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private void renderWidgetTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        FillerBlockEntity filler = filler();
        if (filler == null) return;
        Component tooltip = null;
        if (in(mouseX, mouseY, leftPos + PATTERN_X, topPos + PATTERN_Y, PATTERN_SIZE, PATTERN_SIZE)) {
            tooltip = tooltipOf(filler.patternStatement.get());
        }
        int param = paramAt(mouseX, mouseY);
        if (param >= 0) tooltip = tooltipOf(filler.patternStatement.get(param));
        if (in(mouseX, mouseY, leftPos + EXCAVATE_X, topPos + BUTTON_Y, 16, 16)) {
            tooltip = Component.translatable("tip.filler.excavate." + (filler.canExcavateSetting() ? "on" : "off"));
        }
        if (in(mouseX, mouseY, leftPos + INVERT_X, topPos + BUTTON_Y, 16, 16)) {
            tooltip = Component.translatable("tip.filler.invert." + (filler.isInverted() ? "on" : "off"));
        }
        if (filler.isLocked() && in(mouseX, mouseY, leftPos + LOCK_X, topPos + LOCK_Y, 16, 16)) tooltip = Component.translatable("gui.buildcraft.locked");
        Pattern source = sourceAt(mouseX, mouseY);
        if (source != null) tooltip = tooltipOf(source);
        if (ledgerWidth > LEDGER_CLOSED_WIDTH || ledgerHeight > LEDGER_CLOSED_HEIGHT) {
            int innerX = ledgerX() + 2 + 16 + LEDGER_GAP, iconY = ledgerY() + LEDGER_GAP;
            if (in(mouseX, mouseY, innerX, iconY + 14, 16, 16)) tooltip = Component.translatable("gui.leftToBreak");
            if (in(mouseX, mouseY, innerX, iconY + 32, 16, 16)) tooltip = Component.translatable("gui.leftToPlace");
        }
        if (tooltip != null) renderTooltip(poseStack, tooltip, mouseX, mouseY);
    }

    // Interaction

    private int paramAt(double mouseX, double mouseY) {
        for (int i = 0; i < PatternStatement.MAX_PARAMS; i++) {
            if (in(mouseX, mouseY, leftPos + PARAM_X + SLOT * i, topPos + PARAM_Y, SLOT, SLOT)) return i;
        }
        return -1;
    }

    @Nullable
    private Pattern sourceAt(double mouseX, double mouseY) {
        Pattern[] found = new Pattern[1];
        forEachSource((pattern, x, y) -> {
            if (in(mouseX, mouseY, x, y, SLOT, SLOT)) found[0] = pattern;
        });
        return found[0];
    }

    private boolean onPattern(double mouseX, double mouseY) {
        return in(mouseX, mouseY, leftPos + PATTERN_X, topPos + PATTERN_Y, PATTERN_SIZE, PATTERN_SIZE);
    }

    private boolean canSetPattern(@Nullable Object value) {
        FillerBlockEntity filler = filler();
        return filler != null && !filler.isLocked() && (value == null || value instanceof Pattern);
    }

    private boolean canSetParam(int index, @Nullable Object value) {
        FillerBlockEntity filler = filler();
        return filler != null && !filler.isLocked() && value instanceof FillerParameter parameter && filler.patternStatement.canSet(index, parameter);
    }

    private void setPattern(@Nullable Object value) {
        Pattern pattern = value instanceof Pattern chosen ? chosen : FillerPatterns.NONE;
        press(FillerBlockEntity.patternButton(FillerPatterns.indexOf(pattern)));
    }

    private void setParam(int index, Object value) {
        press(FillerBlockEntity.parameterButton(index, (FillerParameter) value));
    }

    private boolean canDropAt(double mouseX, double mouseY) {
        if (onPattern(mouseX, mouseY)) return canSetPattern(dragged);
        int param = paramAt(mouseX, mouseY);
        return param >= 0 && canSetParam(param, dragged);
    }

    private Variant createVariant(int x, int y, List<Object> possible, Predicate<Object> canSet, Selector select) {
        int count = Math.min(OFFSET_HOVER.length, possible.size());
        Object[] options = possible.subList(0, count).toArray();
        int[][] positions = new int[count][];
        for (int i = 0; i < count; i++) positions[i] = new int[] {x + OFFSET_HOVER[i][0] * SLOT, y + OFFSET_HOVER[i][1] * SLOT};
        int size = 8 + SLOT * (count > 9 ? 3 : 1) + SLOT * 2;
        return new Variant(x, y, size, options, positions, canSet, select);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        FillerBlockEntity filler = filler();
        if (filler == null) return super.mouseClicked(mouseX, mouseY, button);
        boolean interactive = !filler.isLocked();
        if (button == 0) {
            Pattern source = sourceAt(mouseX, mouseY);
            if (source != null) {
                dragging = true;
                dragged = source;
                return true;
            }
            if (onPattern(mouseX, mouseY)) {
                if (!interactive) return true;
                if (Screen.hasShiftDown()) {
                    setPattern(null);
                    return true;
                }
                List<Object> possible = new ArrayList<>();
                possible.add(null);
                possible.addAll(FillerPatterns.PATTERNS);
                variant = createVariant(leftPos + PATTERN_X, topPos + PATTERN_Y, possible, this::canSetPattern, this::setPattern);
                return true;
            }
            int param = paramAt(mouseX, mouseY);
            if (param >= 0) {
                FillerParameter current = filler.patternStatement.get(param);
                if (interactive && current != null) {
                    variant = createVariant(leftPos + PARAM_X + SLOT * param, topPos + PARAM_Y, new ArrayList<>(current.possibleForGui()),
                            value -> canSetParam(param, value), value -> setParam(param, value));
                }
                return true;
            }
            if (in(mouseX, mouseY, leftPos + EXCAVATE_X, topPos + BUTTON_Y, 16, 16)) {
                clickSound();
                press(FillerBlockEntity.BUTTON_EXCAVATE + (filler.canExcavateSetting() ? 0 : 1));
                return true;
            }
            if (in(mouseX, mouseY, leftPos + INVERT_X, topPos + BUTTON_Y, 16, 16)) {
                clickSound();
                press(FillerBlockEntity.BUTTON_INVERT + (filler.isInverted() ? 0 : 1));
                return true;
            }
            if (in(mouseX, mouseY, ledgerX(), ledgerY(), ledgerWidth, ledgerHeight)) {
                ledgerOpen = !ledgerOpen;
                return true;
            }
        } else if (button == 1) {
            if (onPattern(mouseX, mouseY)) {
                dragging = true;
                dragged = filler.patternStatement.get();
                return true;
            }
            int param = paramAt(mouseX, mouseY);
            if (param >= 0) {
                dragging = true;
                dragged = filler.patternStatement.get(param);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (variant != null) {
            Variant closing = variant;
            variant = null;
            for (int i = 0; i < closing.options().length; i++) {
                Object option = closing.options()[i];
                if (option != null && in(mouseX, mouseY, closing.positions()[i][0], closing.positions()[i][1], SLOT, SLOT) && closing.canSet().test(option)) {
                    closing.select().select(option);
                }
            }
            return true;
        }
        if (dragging) {
            if (onPattern(mouseX, mouseY) && canSetPattern(dragged)) setPattern(dragged);
            int param = paramAt(mouseX, mouseY);
            if (param >= 0 && canSetParam(param, dragged)) setParam(param, dragged);
            dragging = false;
            dragged = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
