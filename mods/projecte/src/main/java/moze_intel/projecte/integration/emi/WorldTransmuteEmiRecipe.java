/*
 * ProjectE 1.19.2 port - EMI integration, shared by the Forge and Fabric builds.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.integration.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import moze_intel.projecte.utils.text.PELang;
import net.minecraft.resources.ResourceLocation;

/**
 * One Philosopher's Stone world transmutation: a block (or fluid) and what right-clicking it turns it into. The second
 * output, when there is one, is the sneaking result. Laid out like upstream's JEI category.
 */
public class WorldTransmuteEmiRecipe extends BasicEmiRecipe {

	public WorldTransmuteEmiRecipe(ResourceLocation id, EmiIngredient input, List<EmiStack> outputs) {
		super(PEEmiPlugin.WORLD_TRANSMUTATION, id, 135, 48);
		this.inputs.add(input);
		this.outputs.addAll(outputs);
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.addSlot(inputs.get(0), 15, 15);
		widgets.addTexture(PEEmiPlugin.arrowTexture(), 55, 18, 22, 15, 0, 0, 22, 15, 32, 32);
		widgets.addTooltipText(List.of(PELang.WORLD_TRANSMUTE_DESCRIPTION.translate()), 55, 18, 22, 15);
		int x = 95;
		for (EmiStack output : outputs) {
			widgets.addSlot(output, x, 15).recipeContext(this);
			x += 18;
		}
	}
}
