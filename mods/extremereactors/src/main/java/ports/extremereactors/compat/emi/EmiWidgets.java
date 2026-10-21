/*
 * Extreme Reactors 1.19.2 port - EMI integration
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.compat.emi;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import it.zerono.mods.extremereactors.gamecontent.multiblock.common.client.screen.CommonIcons;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import ports.extremereactors.corelib.CodeHelper;
import ports.extremereactors.corelib.base.CommonConstants;
import ports.extremereactors.corelib.client.gui.Orientation;
import ports.extremereactors.corelib.client.render.ModRenderHelper;
import ports.extremereactors.corelib.energy.EnergySystem;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.platform.Platform;

import java.util.Collection;
import java.util.List;

/**
 * Stack conversions and the widgets the Extreme Reactors recipes share. Fluid amounts are given in millibuckets and
 * converted to the loader's unit, which is what EMI expects.
 */
final class EmiWidgets {

    static long fluidAmount(final long millibuckets) {
        return millibuckets * Platform.backend().fluidBucketVolume() / 1000L;
    }

    static int tankCapacity(final long millibuckets) {
        return Math.toIntExact(fluidAmount(millibuckets));
    }

    static EmiStack fluid(final FluidStack stack) {
        return EmiStack.of(stack.getFluid(), stack.hasTag() ? stack.getTag() : null, fluidAmount(stack.getAmount()));
    }

    static EmiIngredient fluids(final Collection<FluidStack> stacks) {
        return EmiIngredient.of(stacks.stream().map(EmiWidgets::fluid).toList());
    }

    static EmiIngredient items(final Collection<ItemStack> stacks) {
        return EmiIngredient.of(stacks.stream().map(EmiStack::of).toList());
    }

    /**
     * The machines' 16x64 energy gauge, filled to show what one recipe costs.
     */
    static void addPowerBar(final WidgetHolder widgets, final int x, final int y, final double capacity,
                            final long cost) {

        final double progress = capacity > 0 ? Math.max(0.0, Math.min(1.0, cost / capacity)) : 0.0;

        widgets.addDrawable(x, y, 16, 64, (matrix, mouseX, mouseY, delta) ->
                ModRenderHelper.paintProgressBar(matrix, CommonIcons.PowerBar.get(), 0, 0, 0, 16, 64,
                        Orientation.BottomToTop, progress));
        widgets.addTooltipText(List.of(
                Component.translatable("compat.bigreactors.jei.common.recipecategory.energy.tooltip.title")
                        .setStyle(CommonConstants.STYLE_TOOLTIP_TITLE),
                CodeHelper.TEXT_EMPTY_LINE,
                Component.literal(String.format("%d %s", cost, EnergySystem.ForgeEnergy.getUnit()))
                        .setStyle(CommonConstants.STYLE_TOOLTIP_VALUE)), x, y, 16, 64);
    }

    private EmiWidgets() {
    }
}
