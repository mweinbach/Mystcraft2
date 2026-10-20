/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.client.screen.control;

import net.minecraft.network.chat.Component;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.control.AbstractCompositeControl;
import ports.extremereactors.corelib.client.gui.control.SwitchButton;
import ports.extremereactors.corelib.client.gui.layout.FixedLayoutEngine;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An ON and an OFF switch, side by side, following the state of a machine.
 * <p>
 * {@code onChange} receives the ON switch whenever it is switched on or off, by the player or by a data update; check
 * {@link ModContainerScreen#isDataUpdateInProgress()} to tell them apart.
 */
public class OnOff
        extends AbstractCompositeControl {

    public static final int WIDTH = 50;
    public static final int HEIGHT = 16;

    public OnOff(final ModContainerScreen<? extends ModContainer> gui, final Supplier<Boolean> isActive,
                 final Consumer<SwitchButton> onChange, final Component onTooltip, final Component offTooltip) {

        super(gui, gui.nextGenericName());

        final String group = this.getName() + "_onoff";
        final SwitchButton on = new SwitchButton(gui, "on", "ON", false, group);
        final SwitchButton off = new SwitchButton(gui, "off", "OFF", true, group);

        on.setLayoutEngineHint(FixedLayoutEngine.hint(0, 0, WIDTH / 2, HEIGHT));
        on.setTooltips(onTooltip);
        on.Activated.subscribe(onChange);
        on.Deactivated.subscribe(onChange);

        off.setLayoutEngineHint(FixedLayoutEngine.hint(WIDTH / 2, 0, WIDTH / 2, HEIGHT));
        off.setTooltips(offTooltip);

        this.setDesiredDimension(WIDTH, HEIGHT);
        this.addControl(on, off);

        gui.addDataBinding(isActive, active -> {

            on.setActive(active);
            off.setActive(!active);
        });
    }
}
