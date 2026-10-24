package com.agricraft.agricraft.compat.rei;

import com.agricraft.agricraft.api.plant.AgriPlant;
import com.agricraft.agricraft.compat.AgriRecipeDisplays;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * One AgriCraft mutation, produce, clipping or growth requirement entry as an REI display. Original port code
 * (upstream AgriCraft 4 has no REI plugin). Only REI's common API, so it also loads on servers.
 */
public class AgriReiDisplay extends BasicDisplay {

	public static final CategoryIdentifier<AgriReiDisplay> MUTATION = CategoryIdentifier.of(AgriRecipeDisplays.MUTATION);
	public static final CategoryIdentifier<AgriReiDisplay> PRODUCE = CategoryIdentifier.of(AgriRecipeDisplays.PRODUCE);
	public static final CategoryIdentifier<AgriReiDisplay> CLIPPING = CategoryIdentifier.of(AgriRecipeDisplays.CLIPPING);
	public static final CategoryIdentifier<AgriReiDisplay> REQUIREMENT = CategoryIdentifier.of(AgriRecipeDisplays.REQUIREMENT);

	private final CategoryIdentifier<AgriReiDisplay> category;
	private final List<AgriPlant> plants;

	private AgriReiDisplay(CategoryIdentifier<AgriReiDisplay> category, List<EntryIngredient> inputs, List<EntryIngredient> outputs,
	                       net.minecraft.resources.ResourceLocation id, List<AgriPlant> plants) {
		super(inputs, outputs, Optional.of(id));
		this.category = category;
		this.plants = plants;
	}

	/** Inputs: the two parent seeds; output: the child seed; plants: parent 1, parent 2, child. */
	public static AgriReiDisplay mutation(AgriRecipeDisplays.Mutation mutation) {
		return new AgriReiDisplay(MUTATION,
				List.of(entry(mutation.parent1Seed()), entry(mutation.parent2Seed())),
				List.of(entry(mutation.childSeed())),
				mutation.id(), List.of(mutation.parent1(), mutation.parent2(), mutation.child()));
	}

	/** Input: the seed; outputs: one entry per product. */
	public static AgriReiDisplay produce(AgriRecipeDisplays.PlantDisplay display) {
		return plant(PRODUCE, display);
	}

	/** Input: the seed; outputs: one entry per clip product. */
	public static AgriReiDisplay clipping(AgriRecipeDisplays.PlantDisplay display) {
		return plant(CLIPPING, display);
	}

	/** Input: the seed; no outputs. */
	public static AgriReiDisplay requirement(AgriRecipeDisplays.PlantDisplay display) {
		return plant(REQUIREMENT, display);
	}

	private static AgriReiDisplay plant(CategoryIdentifier<AgriReiDisplay> category, AgriRecipeDisplays.PlantDisplay display) {
		List<EntryIngredient> outputs = display.outputs().stream().map(AgriReiDisplay::entry).toList();
		return new AgriReiDisplay(category, List.of(entry(display.seed())), outputs, display.id(), List.of(display.plant()));
	}

	private static EntryIngredient entry(ItemStack stack) {
		return EntryIngredients.ofItemStacks(List.of(stack.copy()));
	}

	/** The plants this display draws: the mutation's parents and child, or the one plant. */
	public List<AgriPlant> plants() {
		return plants;
	}

	@Override
	public CategoryIdentifier<?> getCategoryIdentifier() {
		return category;
	}

}
