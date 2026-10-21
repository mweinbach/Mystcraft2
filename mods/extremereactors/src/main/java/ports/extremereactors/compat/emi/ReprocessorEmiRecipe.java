/*
 * Extreme Reactors 1.19.2 port - EMI integration
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.compat.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import it.zerono.mods.extremereactors.ExtremeReactors;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reprocessor.MultiblockReprocessor;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reprocessor.recipe.ReprocessorRecipe;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A reprocessor recipe, laid out like the JEI category: the waste and fluid going in, the energy it takes and the
 * item coming out.
 */
final class ReprocessorEmiRecipe extends BasicEmiRecipe {

    ReprocessorEmiRecipe(final EmiRecipeCategory category, final ReprocessorRecipe recipe) {

        super(category, recipe.getId(), 179, 106);
        this.inputs = List.of(EmiWidgets.items(recipe.getIngredient1().getMatchingElements()),
                EmiWidgets.fluids(recipe.getIngredient2().getMatchingElements()));
        this.outputs = List.of(EmiStack.of(recipe.getResult().getResult()));
    }

    @Override
    public void addWidgets(final WidgetHolder widgets) {

        widgets.addTexture(BACKGROUND, 5, 5, 96, 96, 0, 0, 96, 96, 96, 96);
        widgets.addSlot(this.inputs.get(0), 83, 5).drawBack(false);
        widgets.addTank(this.inputs.get(1), 33, 22, 18, 66, EmiWidgets.tankCapacity(MultiblockReprocessor.FLUID_CAPACITY))
                .drawBack(false);
        widgets.addSlot(this.outputs.get(0), 83, 70).drawBack(false).recipeContext(this);
        EmiWidgets.addPowerBar(widgets, 6, 23, MultiblockReprocessor.ENERGY_CAPACITY,
                (long) MultiblockReprocessor.TICKS * MultiblockReprocessor.TICK_ENERGY_COST);
    }

    private static final ResourceLocation BACKGROUND = ExtremeReactors.newID("textures/gui/jei/reprocessor.png");
}
