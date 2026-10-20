/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.client.screen.control;

import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;
import ports.extremereactors.corelib.base.client.screen.BaseIcons;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.control.Picture;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import java.util.List;
import java.util.function.Consumer;

/**
 * A small light showing whether a machine is running, with a tooltip for each state.
 */
public class MachineStatusIndicator
        extends Picture
        implements Consumer<Boolean> {

    public MachineStatusIndicator(final ModContainerScreen<? extends ModContainer> gui, final String name) {

        super(gui, name, BaseIcons.MachineStatusOff);
        this._activeTooltips = ImmutableList.of();
        this._inactiveTooltips = ImmutableList.of();
        this.setDesiredDimension(SIZE, SIZE);
    }

    public void setTooltips(final boolean active, final String translationKey) {

        final List<Component> lines = ImmutableList.of(Component.translatable(translationKey));

        if (active) {
            this._activeTooltips = lines;
        } else {
            this._inactiveTooltips = lines;
        }

        this.accept(this._active);
    }

    //region Consumer<Boolean>

    @Override
    public void accept(final Boolean active) {

        this._active = active;
        this.setSprite(active ? BaseIcons.MachineStatusOn : BaseIcons.MachineStatusOff);
        this.setTooltips(active ? this._activeTooltips : this._inactiveTooltips);
    }

    //endregion
    //region internals

    private static final int SIZE = 10;

    private boolean _active;
    private List<Component> _activeTooltips;
    private List<Component> _inactiveTooltips;

    //endregion
}
