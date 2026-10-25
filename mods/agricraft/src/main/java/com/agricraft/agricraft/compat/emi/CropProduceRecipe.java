package com.agricraft.agricraft.compat.emi;

import com.agricraft.agricraft.api.plant.AgriPlant;
import com.agricraft.agricraft.client.gui.AgriGuiGraphics;
import com.agricraft.agricraft.compat.AgriRecipeDisplays;
import com.agricraft.agricraft.compat.client.PlantRendering;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A plant's seed and its possible products. Ported from AgriCraft 4.0.6. */
public class CropProduceRecipe implements EmiRecipe {
	public static final EmiTexture BACKGROUND = new EmiTexture(AgriRecipeDisplays.PRODUCE_TEXTURE, 0, 0, 128, 128, 128, 128, 128, 128);

	private final ResourceLocation id;
	private final EmiRecipeCategory category;
	private final List<EmiIngredient> input;
	private final AgriPlant plant;
	private final List<EmiStack> output;

	public CropProduceRecipe(AgriRecipeDisplays.PlantDisplay display) {
		this(display, AgriCraftEmiPlugin.PRODUCE_CATEGORY);
	}

	protected CropProduceRecipe(AgriRecipeDisplays.PlantDisplay display, EmiRecipeCategory category) {
		this.id = display.id();
		this.category = category;
		input = List.of(AgriCraftEmiPlugin.seed(display.seed()));
		this.plant = display.plant();
		output = new ArrayList<>();
		display.outputs().forEach(product -> output.add(EmiStack.of(product).comparison(Comparison.compareNbt())));
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return category;
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
		return 128;
	}

	@Override
	public int getDisplayHeight() {
		return 128;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.addTexture(BACKGROUND, 0, 0);
		widgets.addSlot(input.get(0), 15, 8);
		widgets.addDrawable(16, 49, 16, 16, (matrices, mouseX, mouseY, delta) -> PlantRendering.drawPlant(new AgriGuiGraphics(matrices), plant, 0, 0));
		widgets.addTooltipText(PlantRendering.tooltip(plant), 16, 49, 16, 16);
		int index = 0;

		for (int y = 32; y < 83; y += 18) {
			if (index >= output.size()) {
				break;
			}
			for (int x = 74; x < 129; x += 18) {
				if (index < output.size()) {
					widgets.addSlot(output.get(index), x, y).drawBack(false).recipeContext(this);
					index++;
				} else {
					break;
				}
			}
		}
	}

}
