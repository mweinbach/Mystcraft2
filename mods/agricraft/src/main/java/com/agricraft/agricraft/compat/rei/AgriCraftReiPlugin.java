package com.agricraft.agricraft.compat.rei;

import com.agricraft.agricraft.common.registry.ModItems;
import com.agricraft.agricraft.compat.AgriRecipeDisplays;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * REI client plugin: AgriCraft's mutation, produce, clipping and growth requirement categories, from the same
 * {@link AgriRecipeDisplays} as the EMI and JEI plugins. Original port code (upstream AgriCraft 4 has no REI
 * plugin). Fabric finds it by the {@code rei_client} entrypoint; on Forge an annotated subclass in the Forge
 * sources registers it.
 */
public class AgriCraftReiPlugin implements REIClientPlugin {

	@Override
	public void registerItemComparators(ItemComparatorRegistry registry) {
		// seeds are told apart by species, as in EMI and JEI
		registry.register((context, stack) -> AgriRecipeDisplays.seedSpecies(stack).hashCode(), ModItems.SEED.get());
	}

	@Override
	public void registerCategories(CategoryRegistry registry) {
		registry.add(new AgriReiCategory.Mutation());
		registry.add(new AgriReiCategory.Products(AgriReiDisplay.PRODUCE, "agricraft.gui.jei.produce", AgriRecipeDisplays.cropSticks().get(0)));
		registry.add(new AgriReiCategory.Products(AgriReiDisplay.CLIPPING, "agricraft.gui.jei.clipping", AgriRecipeDisplays.clipper()));
		registry.add(new AgriReiCategory.Requirement());
		for (ItemStack sticks : AgriRecipeDisplays.cropSticks()) {
			registry.addWorkstations(AgriReiDisplay.MUTATION, entry(sticks));
			registry.addWorkstations(AgriReiDisplay.PRODUCE, entry(sticks));
			registry.addWorkstations(AgriReiDisplay.REQUIREMENT, entry(sticks));
		}
		registry.addWorkstations(AgriReiDisplay.CLIPPING, entry(AgriRecipeDisplays.clipper()));
		registry.addWorkstations(AgriReiDisplay.REQUIREMENT, entry(AgriRecipeDisplays.farmland()));
	}

	private static EntryIngredient entry(ItemStack stack) {
		return EntryIngredients.ofItemStacks(List.of(stack));
	}

	@Override
	public void registerDisplays(DisplayRegistry registry) {
		AgriRecipeDisplays.mutations().forEach(mutation -> registry.add(AgriReiDisplay.mutation(mutation)));
		AgriRecipeDisplays.produce().forEach(display -> registry.add(AgriReiDisplay.produce(display)));
		AgriRecipeDisplays.clippings().forEach(display -> registry.add(AgriReiDisplay.clipping(display)));
		AgriRecipeDisplays.requirements().forEach(display -> registry.add(AgriReiDisplay.requirement(display)));
	}

}
