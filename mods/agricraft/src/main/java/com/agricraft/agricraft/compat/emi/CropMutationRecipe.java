package com.agricraft.agricraft.compat.emi;

import com.agricraft.agricraft.compat.AgriRecipeDisplays;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Two parent seeds give a child seed. Ported from AgriCraft 4.0.6. */
public class CropMutationRecipe implements EmiRecipe {

	private final ResourceLocation id;
	private final List<EmiIngredient> input;
	private final List<EmiStack> output;

	public CropMutationRecipe(AgriRecipeDisplays.Mutation mutation) {
		this.id = mutation.id();
		input = List.of(AgriCraftEmiPlugin.seed(mutation.parent1Seed()), AgriCraftEmiPlugin.seed(mutation.parent2Seed()));
		output = List.of(AgriCraftEmiPlugin.seed(mutation.childSeed()));
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return AgriCraftEmiPlugin.MUTATION_CATEGORY;
	}

	@Override
	public @Nullable ResourceLocation getId() {
		return this.id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return input;
	}

	@Override
	public List<EmiStack> getOutputs() {
		return output;
	}

	@Override
	public int getDisplayWidth() {
		return 125;
	}

	@Override
	public int getDisplayHeight() {
		return 18;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.addSlot(input.get(0), 0, 0);
		widgets.addSlot(input.get(1), 49, 0);
		widgets.addSlot(output.get(0), 107, 0).recipeContext(this);
		widgets.addTexture(EmiTexture.PLUS, 27, 3);
		widgets.addTexture(EmiTexture.EMPTY_ARROW, 75, 1);
	}

}
