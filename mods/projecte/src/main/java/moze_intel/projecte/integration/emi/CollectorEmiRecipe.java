/*
 * ProjectE 1.19.2 port - EMI integration, shared by the Forge and Fabric builds.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.integration.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import moze_intel.projecte.utils.text.PELang;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * An Energy Collector fuel upgrade: the fuel in the upgrade slot, what the collector turns it into, and the EMC that costs.
 * Laid out like upstream's JEI category.
 */
public class CollectorEmiRecipe extends BasicEmiRecipe {

	private final long upgradeEmc;

	public CollectorEmiRecipe(ResourceLocation id, ItemStack input, ItemStack output, long upgradeEmc) {
		super(PEEmiPlugin.COLLECTOR, id, 135, 48);
		this.inputs.add(EmiStack.of(input));
		this.outputs.add(EmiStack.of(output));
		this.upgradeEmc = upgradeEmc;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		Component emc = PELang.EMC.translate(upgradeEmc);
		widgets.addText(emc, (getDisplayWidth() - Minecraft.getInstance().font.width(emc)) / 2, 5, 0x808080, false);
		widgets.addSlot(inputs.get(0), 15, 15);
		widgets.addTexture(PEEmiPlugin.arrowTexture(), 55, 18, 22, 15, 0, 0, 22, 15, 32, 32);
		widgets.addSlot(outputs.get(0), 103, 15).recipeContext(this);
	}
}
