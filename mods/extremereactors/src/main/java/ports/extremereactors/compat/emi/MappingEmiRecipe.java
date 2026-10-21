/*
 * Extreme Reactors 1.19.2 port - EMI integration
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.compat.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import it.zerono.mods.extremereactors.ExtremeReactors;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * One input turning into one output, on the JEI plugin's mapping background: reactions, reactant, coolant and vapor
 * sources, vaporization and condensation.
 */
final class MappingEmiRecipe extends BasicEmiRecipe {

    MappingEmiRecipe(final EmiRecipeCategory category, final String name, final EmiIngredient input,
                     final EmiStack output) {

        super(category, ExtremeReactors.newID("/" + category.getId().getPath() + "/" + name), 144, 56);
        this.inputs = List.of(input);
        this.outputs = List.of(output);
    }

    @Override
    public void addWidgets(final WidgetHolder widgets) {

        widgets.addTexture(BACKGROUND, 0, 0, 144, 56, 0, 0, 144, 56, 144, 56);
        widgets.addSlot(this.inputs.get(0), 32, 19).drawBack(false);
        widgets.addSlot(this.outputs.get(0), 94, 19).drawBack(false).recipeContext(this);
    }

    private static final ResourceLocation BACKGROUND = ExtremeReactors.newID("textures/gui/jei/mapping.png");
}
