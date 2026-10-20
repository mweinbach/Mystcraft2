/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.client.screen.control;

import ports.extremereactors.corelib.base.client.screen.BaseIcons;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.control.AbstractCompositeControl;
import ports.extremereactors.corelib.client.gui.control.GaugeBar;
import ports.extremereactors.corelib.client.gui.control.Picture;
import ports.extremereactors.corelib.client.gui.layout.VerticalLayoutEngine;
import ports.extremereactors.corelib.client.gui.sprite.ISprite;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import java.util.function.Supplier;

/**
 * A 16 by 16 icon over an 18 by 66 framed bar, as the gauges of the Extreme Reactors controllers. Both show the
 * tooltips of the whole control.
 */
abstract class AbstractVerticalBar
        extends AbstractCompositeControl {

    public static final int WIDTH = 18;
    public static final int HEIGHT = 84;

    protected AbstractVerticalBar(final ModContainerScreen<? extends ModContainer> gui, final String name,
                                  final Supplier<ISprite> icon, final GaugeBar bar) {

        super(gui, name);
        this._bar = bar;

        final Picture picture = new Picture(gui, name + "_icon", icon);

        picture.setDesiredDimension(ICON_SIZE, ICON_SIZE);
        picture.useTooltipsFrom(this);

        bar.setDesiredDimension(WIDTH, BAR_HEIGHT);
        bar.setBackground(BaseIcons.BarBackground.get());
        bar.setPadding(1);
        bar.useTooltipsFrom(this);

        this.setDesiredDimension(WIDTH, HEIGHT);
        this.setLayoutEngine(new VerticalLayoutEngine()
                .setZeroMargins()
                .setControlsSpacing(2));
        this.addControl(picture, bar);
    }

    protected GaugeBar getBar() {
        return this._bar;
    }

    private static final int ICON_SIZE = 16;
    private static final int BAR_HEIGHT = 66;

    private final GaugeBar _bar;
}
