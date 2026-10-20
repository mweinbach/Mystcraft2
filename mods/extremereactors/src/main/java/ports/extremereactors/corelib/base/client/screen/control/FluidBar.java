/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.client.screen.control;

import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;
import ports.extremereactors.corelib.CodeHelper;
import ports.extremereactors.corelib.base.CommonConstants;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.control.GaugeBar;
import ports.extremereactors.corelib.client.gui.sprite.ISprite;
import ports.extremereactors.corelib.client.gui.sprite.Sprite;
import ports.extremereactors.corelib.client.render.ModRenderHelper;
import ports.extremereactors.corelib.fluid.FluidHelper;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * A tank gauge: an icon over a bar filled with the fluid in the tank, in proportion to the amount.
 */
public class FluidBar
        extends AbstractVerticalBar {

    public FluidBar(final ModContainerScreen<? extends ModContainer> gui, final String name, final int capacity,
                    final Supplier<Double> amount, final Supplier<FluidStack> fluid, final Supplier<ISprite> icon,
                    final String titleKey, @Nullable final String extraTooltipKey) {

        super(gui, name, icon, new GaugeBar(gui, name + "_bar", capacity, Sprite.EMPTY));
        this._fluid = FluidStack.EMPTY;

        final ImmutableList.Builder<Component> lines = ImmutableList.builder();

        lines.add(Component.translatable(titleKey).setStyle(CommonConstants.STYLE_TOOLTIP_TITLE));
        lines.add(Component.literal("  @0"));
        lines.add(Component.literal("  @1").append(" / " + CodeHelper.formatAsMillibuckets(capacity)));
        lines.add(Component.literal("  @2").append(Component.translatable("gui.bigreactors.corelib.bar.full")));

        if (null != extraTooltipKey) {

            lines.add(CodeHelper.TEXT_EMPTY_LINE);
            lines.add(Component.translatable(extraTooltipKey));
        }

        this.setTooltips(lines.build(), ImmutableList.of(
                (Supplier<Component>) () -> (this._fluid.isEmpty() ? Component.translatable("gui.bigreactors.generic.empty") :
                        FluidHelper.getFluidName(this._fluid)).setStyle(CommonConstants.STYLE_TOOLTIP_VALUE),
                (Supplier<Component>) () -> Component.literal(CodeHelper.formatAsMillibuckets(
                        (int) this.getBar().getValue())).setStyle(CommonConstants.STYLE_TOOLTIP_VALUE),
                (Supplier<Component>) () -> Component.literal(String.format("%d",
                        (int) (this.getBar().getFillRatio() * 100))).setStyle(CommonConstants.STYLE_TOOLTIP_VALUE)));

        gui.addDataBinding(amount, value -> this.getBar().setValue(value));
        gui.addDataBinding(fluid, this::setFluid);
    }

    //region internals

    private void setFluid(final FluidStack stack) {

        this._fluid = stack.copy();

        if (stack.isEmpty()) {

            this.getBar().setBarSprite(Sprite.EMPTY);

        } else {

            this.getBar().setBarSprite(ModRenderHelper.getFlowingFluidSprite(stack));
            this.getBar().setBarSpriteTint(ModRenderHelper.getFluidTintColour(stack));
        }
    }

    private FluidStack _fluid;

    //endregion
}
