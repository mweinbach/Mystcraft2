/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.client.screen.control;

import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;
import ports.extremereactors.corelib.CodeHelper;
import ports.extremereactors.corelib.base.CommonConstants;
import ports.extremereactors.corelib.base.client.screen.BaseIcons;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.control.GaugeBar;
import ports.extremereactors.corelib.data.WideAmount;
import ports.extremereactors.corelib.energy.EnergySystem;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * An energy buffer gauge: a battery icon over a bar showing how full the buffer is.
 */
public class EnergyBar
        extends AbstractVerticalBar {

    public EnergyBar(final ModContainerScreen<? extends ModContainer> gui, final String name,
                     final EnergySystem system, final WideAmount capacity, final Supplier<WideAmount> stored,
                     @Nullable final String extraTooltipKey) {

        super(gui, name, BaseIcons.PowerBattery, new GaugeBar(gui, name + "_bar", capacity.doubleValue(),
                BaseIcons.PowerBar.get()));
        this._stored = Objects.requireNonNull(stored);

        final double max = capacity.doubleValue();
        final String unit = system.getUnit();
        final ImmutableList.Builder<Component> lines = ImmutableList.builder();

        lines.add(Component.translatable("gui.bigreactors.corelib.energybar.title")
                .setStyle(CommonConstants.STYLE_TOOLTIP_TITLE));
        lines.add(Component.literal("  @0").append(" / " + CodeHelper.formatAsHumanReadableNumber(max, unit)));
        lines.add(Component.literal("  @1").append(Component.translatable("gui.bigreactors.corelib.bar.full")));

        if (null != extraTooltipKey) {

            lines.add(CodeHelper.TEXT_EMPTY_LINE);
            lines.add(Component.translatable(extraTooltipKey));
        }

        this.setTooltips(lines.build(), ImmutableList.of(
                (Supplier<Component>) () -> Component.literal(CodeHelper.formatAsHumanReadableNumber(
                        this.getBar().getValue(), unit)).setStyle(CommonConstants.STYLE_TOOLTIP_VALUE),
                (Supplier<Component>) () -> Component.literal(String.format("%d",
                        (int) (this.getBar().getFillRatio() * 100))).setStyle(CommonConstants.STYLE_TOOLTIP_VALUE)));

        gui.addDataBinding(this._stored, amount -> this.getBar().setValue(amount.doubleValue()));
    }

    private final Supplier<WideAmount> _stored;
}
