package ports.buildcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
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
    private static final ResourceLocation LOCK = BuildCraft.id("textures/gui/lock.png");
    private static final int PATTERN_X = 12, PATTERN_Y = 32, PATTERN_SIZE = 32;
    private static final int PARAM_X = 53, PARAM_Y = 39;
    private static final int EXCAVATE_X = 130, INVERT_X = 152, BUTTON_Y = 40;
    private static final int LOCK_X = 12, LOCK_Y = 16;
    private static final int LEDGER_COLOUR = 0xFF6CD41F;
    private static final int LEDGER_TITLE_COLOUR = 0xFFE1C92F;
    private static final int SLOT = SlotRing.SLOT;

    private final Ledger ledger = new Ledger("filler.progress", LEDGER_COLOUR);
    @Nullable private SlotRing variant;
    private boolean dragging;
    @Nullable private Object dragged;

    public FillerScreen(FillerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BuildCraft.id("textures/gui/filler.png"), 176, 241);
    }

    @Override
    protected void init() {
        super.init();
        ledger.place(leftPos + imageWidth, topPos + 5, ledgerMaxWidth(), ledgerMaxHeight());
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

    private int ledgerMaxWidth() {
        int textWidth = font.width(Component.translatable("gui.progress"));
        FillerBlockEntity filler = filler();
        if (filler != null) {
            textWidth = Math.max(textWidth, 20 + font.width(Integer.toString(filler.getCountToBreak())));
            textWidth = Math.max(textWidth, 20 + font.width(Integer.toString(filler.getCountToPlace())));
        }
        return Math.max(Ledger.CLOSED_WIDTH, 2 + 16 + Ledger.GAP + textWidth) + Ledger.GAP * 2;
    }

    private int ledgerMaxHeight() {
        return Ledger.GAP + 14 + 16 + 2 + 16 + Ledger.GAP * 2;
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
        if (ledger.contains(mouseX, mouseY)) return false;
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    // Ticking

    @Override
    protected void containerTick() {
        super.containerTick();
        ledger.tick(ledgerMaxWidth(), ledgerMaxHeight());
    }

    // Drawing

    private void drawSlot(PoseStack poseStack, @Nullable Object slot, int x, int y) {
        RenderSystem.setShaderTexture(0, SlotRing.MISC_SLOTS);
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
                RenderSystem.setShaderTexture(0, SlotRing.MISC_SLOTS);
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
        ledger.render(poseStack, partialTicks, () -> {
            int iconY = ledger.iconY();
            itemRenderer.renderGuiItem(new ItemStack(Items.IRON_INGOT), ledger.iconX(), iconY);
            if (!ledger.isExpanded()) return;
            int innerX = ledger.innerX();
            font.drawShadow(poseStack, Component.translatable("gui.progress"), innerX, iconY + 3, LEDGER_TITLE_COLOUR);
            itemRenderer.renderGuiItem(new ItemStack(Items.IRON_PICKAXE), innerX, iconY + 14);
            font.draw(poseStack, Integer.toString(filler.getCountToBreak()), innerX + 20, iconY + 14 + 5, 0xFF333333);
            itemRenderer.renderGuiItem(new ItemStack(Blocks.BRICKS), innerX, iconY + 32);
            font.draw(poseStack, Integer.toString(filler.getCountToPlace()), innerX + 20, iconY + 32 + 5, 0xFF333333);
        });
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
        if (variant != null) variant.render(poseStack, this::drawSlot);
        if (dragging) renderDragged(poseStack, mouseX, mouseY);
        poseStack.popPose();
        if (variant != null) renderVariantTooltip(poseStack, mouseX, mouseY);
        else if (!dragging && menu.getCarried().isEmpty()) renderWidgetTooltip(poseStack, mouseX, mouseY);
    }

    private void renderVariantTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        Component tooltip = tooltipOf(variant.optionAt(mouseX, mouseY));
        if (tooltip != null) renderTooltip(poseStack, tooltip, mouseX, mouseY);
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
        if (ledger.isExpanded()) {
            int innerX = ledger.innerX(), iconY = ledger.iconY();
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
                variant = new SlotRing(leftPos + PATTERN_X, topPos + PATTERN_Y, possible, this::canSetPattern, this::setPattern);
                return true;
            }
            int param = paramAt(mouseX, mouseY);
            if (param >= 0) {
                FillerParameter current = filler.patternStatement.get(param);
                if (interactive && current != null) {
                    variant = new SlotRing(leftPos + PARAM_X + SLOT * param, topPos + PARAM_Y, new ArrayList<>(current.possibleForGui()),
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
            if (ledger.contains(mouseX, mouseY)) {
                ledger.toggle();
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
            SlotRing closing = variant;
            variant = null;
            closing.release(mouseX, mouseY);
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
