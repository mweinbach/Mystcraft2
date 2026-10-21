/*
 * Extreme Reactors 1.19.2 port - EMI integration
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.compat.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.WidgetHolder;
import it.zerono.mods.extremereactors.ExtremeReactors;
import it.zerono.mods.extremereactors.config.Config;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.MultiblockFluidizer;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.FluidizerFluidMixingRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.FluidizerSolidMixingRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.FluidizerSolidRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.IFluidizerRecipe;
import net.minecraft.resources.ResourceLocation;
import ports.extremereactors.corelib.recipe.ModRecipe;

import java.util.List;

/**
 * A fluidizer recipe of any of the three kinds, laid out like the JEI categories.
 */
final class FluidizerEmiRecipe extends BasicEmiRecipe {

    static FluidizerEmiRecipe solid(final EmiRecipeCategory category, final FluidizerSolidRecipe recipe) {
        return new FluidizerEmiRecipe(category, recipe, recipe, List.of(EmiWidgets.items(recipe.getIngredient().getMatchingElements())));
    }

    static FluidizerEmiRecipe solidMixing(final EmiRecipeCategory category, final FluidizerSolidMixingRecipe recipe) {
        return new FluidizerEmiRecipe(category, recipe, recipe, List.of(
                EmiWidgets.items(recipe.getIngredient1().getMatchingElements()),
                EmiWidgets.items(recipe.getIngredient2().getMatchingElements())));
    }

    static FluidizerEmiRecipe fluidMixing(final EmiRecipeCategory category, final FluidizerFluidMixingRecipe recipe) {
        return new FluidizerEmiRecipe(category, recipe, recipe, List.of(
                EmiWidgets.fluids(recipe.getIngredient1().getMatchingElements()),
                EmiWidgets.fluids(recipe.getIngredient2().getMatchingElements())));
    }

    @Override
    public void addWidgets(final WidgetHolder widgets) {

        final int ordinal = this._type.ordinal() - 1;
        final int ticks = this._type.getTicks();

        widgets.addTexture(BACKGROUND, 5, 5, 128, 88, 1 == ordinal ? 128 : 0, 2 == ordinal ? 88 : 0);
        EmiWidgets.addPowerBar(widgets, 6, 23, MultiblockFluidizer.ENERGY_CAPACITY.doubleValue(), this._energyCost);
        widgets.addTank(this.outputs.get(0), 70, 22, 18, 66, EmiWidgets.tankCapacity(OUTPUT_CAPACITY))
                .drawBack(false).recipeContext(this);
        widgets.addAnimatedTexture(BACKGROUND, 45, 47, 24, 16, 0, 176, ticks * 50, true, false, false);

        if (IFluidizerRecipe.Type.Solid != this._type) {
            widgets.addAnimatedTexture(BACKGROUND, 89, 47, 24, 16, 0, 192, ticks * 50, true, true, false);
        }

        if (IFluidizerRecipe.Type.FluidMixing == this._type) {

            final int capacity = EmiWidgets.tankCapacity(FluidizerFluidMixingRecipe.getMaxResultAmount());

            widgets.addTank(this.inputs.get(0), 25, 22, 18, 66, capacity).drawBack(false);
            widgets.addTank(this.inputs.get(1), 115, 22, 18, 66, capacity).drawBack(false);

        } else {

            widgets.addSlot(this.inputs.get(0), 25, 46).drawBack(false);

            if (this.inputs.size() > 1) {
                widgets.addSlot(this.inputs.get(1), 115, 46).drawBack(false);
            }
        }
    }

    //region internals

    private FluidizerEmiRecipe(final EmiRecipeCategory category, final ModRecipe recipe,
                               final IFluidizerRecipe fluidizerRecipe, final List<EmiIngredient> inputs) {

        super(category, recipe.getId(), 138, 98);
        this._type = fluidizerRecipe.getRecipeType();
        this._energyCost = (long) Config.COMMON.fluidizer.energyPerRecipeTick.get()
                * fluidizerRecipe.getEnergyUsageMultiplier() * this._type.getTicks();
        this.inputs = inputs;
        this.outputs = List.of(EmiWidgets.fluid(fluidizerRecipe.getResult().getResult()));
    }

    private static final long OUTPUT_CAPACITY = 20_000;
    private static final ResourceLocation BACKGROUND = ExtremeReactors.newID("textures/gui/jei/fluidizer.png");

    private final IFluidizerRecipe.Type _type;
    private final long _energyCost;

    //endregion
}
