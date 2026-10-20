/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import ports.extremereactors.corelib.client.gui.control.AbstractSwitchButton;
import ports.extremereactors.corelib.client.gui.control.SlotsGroup;
import ports.extremereactors.corelib.client.gui.databind.BindingGroup;
import ports.extremereactors.corelib.client.gui.databind.MonoConsumerBinding;
import ports.extremereactors.corelib.client.gui.sprite.ISprite;
import ports.extremereactors.corelib.client.render.ModRenderHelper;
import ports.extremereactors.corelib.data.geometry.Rectangle;
import ports.extremereactors.corelib.data.gfx.Colour;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;
import ports.extremereactors.corelib.item.inventory.container.slot.type.SlotType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A container screen made of {@link IControl}s.
 * <p>
 * Subclasses build their controls in {@link #onScreenCreate()}, which runs once, when the screen is first shown, and
 * hand the root container to {@link #createWindow}. Every time the screen is initialized (on resize too) the window
 * is laid out and the container slots are moved under the {@link SlotsGroup}s that show them; slots that no group
 * shows are moved out of sight.
 * <p>
 * {@link #onDataUpdated()} runs once the window is laid out and whenever the container data changes; subclasses
 * update their controls there, usually through {@link #addDataBinding}.
 */
public class ModContainerScreen<C extends ModContainer>
        extends AbstractContainerScreen<C> {

    /** Builds the button that opens a handbook page, when a handbook mod is available. */
    @FunctionalInterface
    public interface IHelpButtonFactory {

        @Nullable
        IControl create(ModContainerScreen<? extends ModContainer> screen, ResourceLocation bookId,
                        ResourceLocation entryId, int pageNumber);
    }

    public static void setHelpButtonFactory(@Nullable final IHelpButtonFactory factory) {
        s_helpButtonFactory = factory;
    }

    /**
     * Read a tooltip width from a translation, so that each language can choose its own.
     *
     * @return the width, or {@code defaultValue} if the translation is missing or not a number
     */
    public static int parseTooltipsPopupMaxWidthFromLang(final String translationKey, final int defaultValue) {

        try {
            return Integer.parseInt(I18n.get(translationKey).trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public ModContainerScreen(final C container, final Inventory inventory, final Component title,
                              final int guiWidth, final int guiHeight) {

        super(container, inventory, title);
        this.imageWidth = guiWidth;
        this.imageHeight = guiHeight;
        this._theme = Theme.DEFAULT;
        this._bindings = new BindingGroup();
        this._switchGroups = new HashMap<>();
        this._dataUpdateHandler = this::runDataUpdate;
        this._errors = List.of();
    }

    //region screen geometry and resources

    public Theme getTheme() {
        return this._theme;
    }

    public void setTheme(final Theme theme) {
        this._theme = theme;
    }

    public int getGuiWidth() {
        return this.imageWidth;
    }

    public int getGuiHeight() {
        return this.imageHeight;
    }

    /** The screen column of the left edge of the GUI. */
    public int getGuiLeft() {
        return this.leftPos;
    }

    /** The screen row of the top edge of the GUI. */
    public int getGuiTop() {
        return this.topPos;
    }

    public Font getFont() {
        return this.font;
    }

    public ItemRenderer getItemRenderer() {
        return this.itemRenderer;
    }

    public double getZLevel() {
        return this.getBlitOffset();
    }

    /** A unique name for a control that does not need a meaningful one. */
    public String nextGenericName() {
        return "control" + (++this._genericNameCounter);
    }

    /** The width tooltip lines are wrapped to, or a value of 0 or less to not wrap them. */
    public int getTooltipsPopupMaxWidth() {
        return -1;
    }

    //endregion
    //region switch groups

    public void addToSwitchGroup(final String group, final AbstractSwitchButton<?> button) {
        this._switchGroups.computeIfAbsent(group, k -> new ArrayList<>()).add(button);
    }

    public Iterable<AbstractSwitchButton<?>> getSwitchGroup(final String group) {
        return List.copyOf(this._switchGroups.getOrDefault(group, List.of()));
    }

    //endregion
    //region data updates

    /**
     * Keep a control in sync with a value: {@code consumer} receives the value the first time the screen is updated
     * and then every time it changes.
     */
    public <T> void addDataBinding(final Supplier<T> supplier, final Consumer<T> consumer) {
        this._bindings.addBinding(new MonoConsumerBinding<>(supplier, Supplier::get, consumer));
    }

    /** True while {@link #onDataUpdated()} runs, so that controls changed by an update do not send commands back. */
    public boolean isDataUpdateInProgress() {
        return this._dataUpdateInProgress;
    }

    /** Update the controls from the data they show. */
    protected void onDataUpdated() {
        this._bindings.update();
    }

    /** Build the controls of the screen; runs once, before the screen is first laid out. */
    protected void onScreenCreate() {
    }

    /** Subscribe to the data update events {@link #onDataUpdated()} should follow. */
    protected void subscribeDataUpdateEvents(final Runnable handler) {
        this.getMenu().DataUpdated.subscribe(handler);
    }

    protected void unsubscribeDataUpdateEvents(final Runnable handler) {
        this.getMenu().DataUpdated.unsubscribe(handler);
    }

    //endregion
    //region window

    /**
     * Set the root container of the screen. It covers the whole GUI area unless it has a desired size.
     *
     * @param mainWindow true for the main window of the screen; it is the only kind supported
     */
    protected void createWindow(final IControlContainer window, final boolean mainWindow) {

        window.setParent(null);
        this._window = window;
    }

    /** Close the screen at the next tick. */
    public void close() {
        this._closeRequested = true;
    }

    /** If true, the inventory key does not close the screen. */
    protected void ignoreCloseOnInventoryKey(final boolean ignore) {
        this._ignoreCloseOnInventoryKey = ignore;
    }

    /**
     * Validate the values of the controls shown. If any is invalid, the problems are shown for a few seconds.
     *
     * @return true if every value is valid
     */
    public boolean isValid() {

        if (null == this._window) {
            return true;
        }

        final List<Component> errors = new ArrayList<>();

        this._window.validate(errors::add);

        if (errors.isEmpty()) {
            return true;
        }

        this._errors = List.copyOf(errors);
        this._errorsShownUntil = Util.getMillis() + ERRORS_DISPLAY_TIME;
        return false;
    }

    //endregion
    //region controls factories

    /** A button that opens a handbook page, or null when no handbook mod is available. */
    @Nullable
    protected IControl createPatchouliHelpButton(final ResourceLocation bookId, final ResourceLocation entryId,
                                                 final int pageNumber) {

        final IHelpButtonFactory factory = s_helpButtonFactory;

        return null != factory ? factory.create(this, bookId, entryId, pageNumber) : null;
    }

    protected SlotsGroup createMonoSlotGroupControl(final String controlName, final String inventoryName,
                                                    final ISprite background, final int slotOffset) {
        return new SlotsGroup(this, controlName, inventoryName, null, 1, 1, background, slotOffset);
    }

    protected SlotsGroup createPlayerHotBarSlotsGroupControl(final ISprite background, final int slotOffset) {
        return new SlotsGroup(this, "playerHotBarSlots", ModContainer.INVENTORYNAME_PLAYER_INVENTORY,
                SlotType.PlayerHotBar, 9, 1, background, slotOffset);
    }

    protected SlotsGroup createPlayerInventorySlotsGroupControl(final ISprite background, final int slotOffset) {
        return new SlotsGroup(this, "playerInventorySlots", ModContainer.INVENTORYNAME_PLAYER_INVENTORY,
                SlotType.PlayerInventory, 9, 3, background, slotOffset);
    }

    //endregion
    //region AbstractContainerScreen

    @Override
    protected void init() {

        super.init();

        final boolean firstInit = !this._created;

        if (firstInit) {

            this._created = true;
            this.onScreenCreate();
            this.subscribeDataUpdateEvents(this._dataUpdateHandler);
        }

        this.layoutWindow();

        if (firstInit) {
            this.runDataUpdate();
        }
    }

    @Override
    public void removed() {

        this.setFocus(null);
        this.unsubscribeDataUpdateEvents(this._dataUpdateHandler);
        super.removed();
    }

    @Override
    protected void containerTick() {

        super.containerTick();

        if (this._closeRequested) {

            this._closeRequested = false;
            this.onClose();
        }
    }

    @Override
    public void render(final PoseStack matrix, final int mouseX, final int mouseY, final float partialTicks) {

        this.renderBackground(matrix);
        super.render(matrix, mouseX, mouseY, partialTicks);
        this.paintErrors(matrix);
        this.paintTooltips(matrix, mouseX, mouseY);
    }

    @Override
    protected void renderBg(final PoseStack matrix, final float partialTicks, final int mouseX, final int mouseY) {

        if (null != this._window) {

            this._window.onPaintBackground(matrix, partialTicks, mouseX, mouseY);
            this._window.onPaint(matrix, partialTicks, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(final PoseStack matrix, final int mouseX, final int mouseY) {
        // every text is painted by the controls
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {

        final Optional<IControl> hit = this.findControl(mouseX, mouseY);

        this.setFocus(hit.filter(c -> c.canFocus() && c.isEnabled()).orElse(null));

        for (IControl control = hit.orElse(null); null != control; control = control.getParent().orElse(null)) {

            if (control.isEnabled() && control.onMouseClicked(mouseX, mouseY, button)) {

                this._mouseCapture = control;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {

        final IControl capture = this._mouseCapture;

        if (null != capture) {

            this._mouseCapture = null;
            capture.onMouseReleased(mouseX, mouseY, button);
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX,
                                final double dragY) {

        final IControl capture = this._mouseCapture;

        if (null != capture) {

            capture.onMouseDragged(mouseX, mouseY, button, dragX, dragY);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double delta) {

        for (IControl control = this.findControl(mouseX, mouseY).orElse(null); null != control;
             control = control.getParent().orElse(null)) {

            if (control.isEnabled() && control.onMouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {

        if (null != this._focused && this._focused.onKeyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (this._ignoreCloseOnInventoryKey && null != this.minecraft &&
                this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(final char character, final int modifiers) {

        if (null != this._focused && this._focused.onCharTyped(character, modifiers)) {
            return true;
        }

        return super.charTyped(character, modifiers);
    }

    //endregion
    //region internals

    private void runDataUpdate() {

        if (this._dataUpdateInProgress) {
            return;
        }

        this._dataUpdateInProgress = true;

        try {
            this.onDataUpdated();
        } finally {
            this._dataUpdateInProgress = false;
        }
    }

    private void layoutWindow() {

        if (null == this._window) {
            return;
        }

        final int width = this._window.getDesiredDimension(DesiredDimension.Width);
        final int height = this._window.getDesiredDimension(DesiredDimension.Height);

        this._window.setBounds(new Rectangle(0, 0, width > 0 ? width : this.imageWidth,
                height > 0 ? height : this.imageHeight));
        this._window.layout();
        this.positionSlots();
    }

    private void positionSlots() {

        final Set<Integer> shown = new HashSet<>();

        this.collectSlotsGroups(this._window, group -> shown.addAll(group.positionSlots(this.getMenu())));

        for (int i = 0; i < this.getMenu().slots.size(); ++i) {
            if (!shown.contains(i)) {
                this.getMenu().repositionSlot(i, HIDDEN_SLOT_POSITION, HIDDEN_SLOT_POSITION);
            }
        }
    }

    private void collectSlotsGroups(@Nullable final IControl control, final Consumer<SlotsGroup> consumer) {

        if (null == control || !control.isVisible()) {
            return;
        }

        if (control instanceof SlotsGroup group) {
            consumer.accept(group);
        } else if (control instanceof IControlContainer container) {
            for (final IControl child : container.getActiveControls()) {
                this.collectSlotsGroups(child, consumer);
            }
        }
    }

    private Optional<IControl> findControl(final double mouseX, final double mouseY) {
        return null != this._window ? this._window.findControl((int) mouseX, (int) mouseY) : Optional.empty();
    }

    private void setFocus(@Nullable final IControl control) {

        if (control == this._focused) {
            return;
        }

        final IControl previous = this._focused;

        this._focused = control;

        if (null != previous) {
            previous.onKillFocus();
        }

        if (null != control) {
            control.onSetFocus();
        }
    }

    private void paintTooltips(final PoseStack matrix, final int mouseX, final int mouseY) {

        if (!this.menu.getCarried().isEmpty()) {
            return;
        }

        if (null != this.hoveredSlot && this.hoveredSlot.hasItem()) {

            this.renderTooltip(matrix, mouseX, mouseY);
            return;
        }

        IControl control = this.findControl(mouseX, mouseY).orElse(null);

        while (null != control && control.getTooltips().isEmpty()) {
            control = control.getParent().orElse(null);
        }

        if (null == control) {
            return;
        }

        final List<Component> lines = TooltipText.expand(control.getTooltips(), control.getTooltipsObjects());
        final int maxWidth = this.getTooltipsPopupMaxWidth();

        if (maxWidth > 0) {

            final List<FormattedCharSequence> wrapped = new ArrayList<>();

            for (final Component line : lines) {

                final List<FormattedCharSequence> parts = this.font.split(line, maxWidth);

                if (parts.isEmpty()) {
                    wrapped.add(FormattedCharSequence.EMPTY);
                } else {
                    wrapped.addAll(parts);
                }
            }

            this.renderTooltip(matrix, wrapped, mouseX, mouseY);

        } else {

            this.renderComponentTooltip(matrix, lines, mouseX, mouseY);
        }
    }

    private void paintErrors(final PoseStack matrix) {

        if (this._errors.isEmpty()) {
            return;
        }

        if (Util.getMillis() > this._errorsShownUntil) {

            this._errors = List.of();
            return;
        }

        final int lineHeight = this.font.lineHeight + 2;
        int width = 0;

        for (final Component line : this._errors) {
            width = Math.max(width, this.font.width(line));
        }

        final int height = this._errors.size() * lineHeight + 6;
        final int x1 = this.leftPos + (this.imageWidth - width) / 2 - 4;
        final int y1 = this.topPos + (this.imageHeight - height) / 2;
        final int z = this.getBlitOffset();

        // over the slot items
        matrix.pushPose();
        matrix.translate(0, 0, ERRORS_Z);
        ModRenderHelper.paintSolidRect(matrix, x1, y1, x1 + width + 8, y1 + height, z, ERRORS_BACKGROUND);
        ModRenderHelper.paintFrame(matrix, x1, y1, x1 + width + 8, y1 + height, z, ERRORS_FRAME);

        int y = y1 + 4;

        for (final Component line : this._errors) {

            this.font.draw(matrix, line.copy().withStyle(ChatFormatting.RED),
                    this.leftPos + (this.imageWidth - this.font.width(line)) / 2.0f, y, 0xFFFFFFFF);
            y += lineHeight;
        }

        matrix.popPose();
    }

    private static final int HIDDEN_SLOT_POSITION = -9999;
    private static final long ERRORS_DISPLAY_TIME = 3000;
    private static final int ERRORS_Z = 400;
    private static final Colour ERRORS_BACKGROUND = new Colour(0x10, 0x10, 0x10, 0xE8);
    private static final Colour ERRORS_FRAME = new Colour(0xAA, 0x00, 0x00, 0xFF);

    @Nullable
    private static volatile IHelpButtonFactory s_helpButtonFactory;

    private final BindingGroup _bindings;
    private final Map<String, List<AbstractSwitchButton<?>>> _switchGroups;
    private final Runnable _dataUpdateHandler;
    private Theme _theme;
    @Nullable
    private IControlContainer _window;
    @Nullable
    private IControl _focused;
    @Nullable
    private IControl _mouseCapture;
    private boolean _created;
    private boolean _dataUpdateInProgress;
    private boolean _closeRequested;
    private boolean _ignoreCloseOnInventoryKey;
    private int _genericNameCounter;
    private List<Component> _errors;
    private long _errorsShownUntil;

    //endregion
}
