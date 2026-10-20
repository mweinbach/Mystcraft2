/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.client.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import ports.extremereactors.corelib.base.CommonConstants;
import ports.extremereactors.corelib.base.multiblock.part.AbstractMultiblockEntity;
import ports.extremereactors.corelib.client.gui.DesiredDimension;
import ports.extremereactors.corelib.client.gui.IControl;
import ports.extremereactors.corelib.client.gui.ModTileContainerScreen;
import ports.extremereactors.corelib.client.gui.control.Label;
import ports.extremereactors.corelib.client.gui.control.Panel;
import ports.extremereactors.corelib.client.gui.control.SlotsGroup;
import ports.extremereactors.corelib.client.gui.layout.FixedLayoutEngine;
import ports.extremereactors.corelib.client.gui.layout.ILayoutEngine;
import ports.extremereactors.corelib.client.gui.sprite.ISprite;
import ports.extremereactors.corelib.client.gui.sprite.Sprite;
import ports.extremereactors.corelib.client.gui.sprite.SpriteTextureMap;
import ports.extremereactors.corelib.item.inventory.PlayerInventoryUsage;
import ports.extremereactors.corelib.item.inventory.container.ModTileContainer;
import ports.extremereactors.multiblock.IMultiblockController;
import ports.extremereactors.multiblock.IMultiblockMachine;
import ports.extremereactors.multiblock.cuboid.AbstractCuboidMultiblockController;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The screen of a multiblock part: a title bar, with an optional status widget and help button, over a content
 * panel, on a background texture whose area at (0, 202) holds the player inventory slot backgrounds.
 * The screen closes when the machine is no longer assembled.
 */
public abstract class AbstractMultiblockScreen<Controller extends AbstractCuboidMultiblockController<Controller> & IMultiblockMachine,
                                               T extends AbstractMultiblockEntity<Controller> & MenuProvider,
                                               C extends ModTileContainer<T>>
        extends ModTileContainerScreen<T, C> {

    public static final Style STYLE_TOOLTIP_TITLE = CommonConstants.STYLE_TOOLTIP_TITLE;
    public static final Style STYLE_TOOLTIP_VALUE = CommonConstants.STYLE_TOOLTIP_VALUE;

    protected AbstractMultiblockScreen(final C container, final Inventory inventory,
                                       final PlayerInventoryUsage inventoryUsage, final Component title,
                                       final Supplier<SpriteTextureMap> mainTexture) {
        this(container, inventory, inventoryUsage, title, DEFAULT_GUI_WIDTH, DEFAULT_GUI_HEIGHT, mainTexture);
    }

    protected AbstractMultiblockScreen(final C container, final Inventory inventory,
                                       final PlayerInventoryUsage inventoryUsage, final Component title,
                                       final int guiWidth, final int guiHeight,
                                       final Supplier<SpriteTextureMap> mainTexture) {

        super(container, inventory, title, guiWidth, guiHeight);
        this._mainTexture = mainTexture.get();
        this._contentPanel = new Panel(this, "content");

        final boolean hotBar = PlayerInventoryUsage.HotBar == inventoryUsage || PlayerInventoryUsage.Both == inventoryUsage;
        final boolean main = PlayerInventoryUsage.MainInventory == inventoryUsage || PlayerInventoryUsage.Both == inventoryUsage;

        this._hotBarSprite = hotBar ? this.inventorySprite(162, 18) : Sprite.EMPTY;
        this._mainInventorySprite = main ? this.inventorySprite(162, 54) : Sprite.EMPTY;
        this._singleSlotSprite = PlayerInventoryUsage.None != inventoryUsage ? this.inventorySprite(18, 18) : Sprite.EMPTY;
    }

    /** A widget shown at the left of the title, as a machine status indicator; null for none. */
    @Nullable
    protected IControl getTitleBarWidget() {
        return null;
    }

    protected void addPatchouliHelpButton(final ResourceLocation bookId, final ResourceLocation entryId,
                                          final int pageNumber) {
        this._helpButton = this.createPatchouliHelpButton(bookId, entryId, pageNumber);
    }

    protected void addControl(final IControl control) {
        this._contentPanel.addControl(control);
    }

    protected void setContentLayoutEngine(final ILayoutEngine engine) {
        this._contentPanel.setLayoutEngine(engine);
    }

    protected Optional<Controller> getMultiblockController() {
        return this.getTileEntity().getMultiblockController();
    }

    protected boolean isMultiblockAssembled() {
        return this.getMultiblockController().map(IMultiblockController::isAssembled).orElse(false);
    }

    //region slot groups

    protected SlotsGroup createSingleSlotGroupControl(final String controlName, final String inventoryName) {
        return this.createMonoSlotGroupControl(controlName, inventoryName, this._singleSlotSprite, 1);
    }

    protected SlotsGroup createPlayerHotBarSlotsGroupControl() {
        return this.createPlayerHotBarSlotsGroupControl(this._hotBarSprite, 1);
    }

    protected SlotsGroup createPlayerInventorySlotsGroupControl() {
        return this.createPlayerInventorySlotsGroupControl(this._mainInventorySprite, 1);
    }

    //endregion
    //region ModTileContainerScreen

    @Override
    protected void onScreenCreate() {

        super.onScreenCreate();

        final int width = this.getGuiWidth();
        final int height = this.getGuiHeight();
        final Panel window = new Panel(this, "mainPanel");
        final Panel titlePanel = new Panel(this, "titlePanel");
        final Label title = new Label(this, "title", this.getTitle().getString());

        window.setDesiredDimension(width, height);
        window.setBackground(this._mainTexture.sprite().ofSize(width, height).build());
        window.setLayoutEngine(new FixedLayoutEngine().setZeroMargins());

        titlePanel.setLayoutEngineHint(FixedLayoutEngine.hint(0, 0, width, TITLE_PANEL_HEIGHT));
        titlePanel.setLayoutEngine(new FixedLayoutEngine().setZeroMargins());

        title.setPadding(2);
        title.setColor(this.getTheme().GUI_TITLE);
        title.setAutoSize(false);
        title.setLayoutEngineHint(FixedLayoutEngine.hint(17, 7, width - 47, 12));
        titlePanel.addControl(title);

        final IControl widget = this.getTitleBarWidget();

        if (null != widget) {

            widget.setLayoutEngineHint(FixedLayoutEngine.hint(7, 7, 10, 10));
            titlePanel.addControl(widget);
        }

        if (null != this._helpButton) {

            this._helpButton.setLayoutEngineHint(FixedLayoutEngine.hint(width - 18, 6, 12, 12));
            titlePanel.addControl(this._helpButton);
        }

        this._contentPanel.setDesiredDimension(DesiredDimension.Width, width);
        this._contentPanel.setDesiredDimension(DesiredDimension.Height, height - TITLE_PANEL_HEIGHT);
        this._contentPanel.setLayoutEngineHint(FixedLayoutEngine.hint(0, TITLE_PANEL_HEIGHT, width,
                height - TITLE_PANEL_HEIGHT));

        window.addControl(titlePanel, this._contentPanel);
        this.createWindow(window, true);
    }

    @Override
    protected void onDataUpdated() {

        super.onDataUpdated();

        if (!this.isMultiblockAssembled()) {
            this.close();
        }
    }

    //endregion
    //region internals

    private ISprite inventorySprite(final int width, final int height) {
        return this._mainTexture.sprite().from(0, 202).ofSize(width, height).build();
    }

    private static final int DEFAULT_GUI_WIDTH = 224;
    private static final int DEFAULT_GUI_HEIGHT = 166;
    private static final int TITLE_PANEL_HEIGHT = 21;

    private final SpriteTextureMap _mainTexture;
    private final Panel _contentPanel;
    private final ISprite _hotBarSprite;
    private final ISprite _mainInventorySprite;
    private final ISprite _singleSlotSprite;
    @Nullable
    private IControl _helpButton;

    //endregion
}
