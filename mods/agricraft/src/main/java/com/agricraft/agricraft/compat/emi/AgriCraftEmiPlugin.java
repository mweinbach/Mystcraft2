package com.agricraft.agricraft.compat.emi;

import com.agricraft.agricraft.api.genetic.AgriGenome;
import com.agricraft.agricraft.common.registry.ModItems;
import com.agricraft.agricraft.compat.AgriRecipeDisplays;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.ItemStack;

/**
 * EMI plugin: the mutation, produce, clipping and growth requirement categories. Forge finds it by its
 * {@link EmiEntrypoint} annotation, Fabric by the {@code emi} entrypoint. Ported from AgriCraft 4.0.6; the
 * recipe lists come from the shared {@link AgriRecipeDisplays}.
 */
@EmiEntrypoint
public class AgriCraftEmiPlugin implements EmiPlugin {

	public static final EmiStack WOODEN_CROP_STICK = EmiStack.of(ModItems.WOODEN_CROP_STICKS.get());
	public static final EmiStack CLIPPER = EmiStack.of(AgriRecipeDisplays.clipper());
	public static final EmiStack FARMLAND = EmiStack.of(AgriRecipeDisplays.farmland());
	public static final EmiRecipeCategory MUTATION_CATEGORY = new EmiRecipeCategory(AgriRecipeDisplays.MUTATION, WOODEN_CROP_STICK);
	public static final EmiRecipeCategory PRODUCE_CATEGORY = new EmiRecipeCategory(AgriRecipeDisplays.PRODUCE, WOODEN_CROP_STICK);
	public static final EmiRecipeCategory CLIPPING_CATEGORY = new EmiRecipeCategory(AgriRecipeDisplays.CLIPPING, CLIPPER);
	public static final EmiRecipeCategory REQUIREMENT_CATEGORY = new EmiRecipeCategory(AgriRecipeDisplays.REQUIREMENT, FARMLAND);

	/** Seeds are told apart by their species only. */
	public static final Comparison COMPARE_SEEDS = Comparison.compareData(stack -> {
		var genome = stack.getNbt() == null ? null : AgriGenome.fromNBT(stack.getNbt());
		if (genome != null) {
			return genome.getSpeciesGene().getDominant().trait();
		} else {
			return "unknown";
		}
	});

	public static Comparison compareSeeds() {
		return COMPARE_SEEDS;
	}

	static EmiStack seed(ItemStack stack) {
		return EmiStack.of(stack).comparison(compareSeeds());
	}

	@Override
	public void register(EmiRegistry registry) {
		registry.addCategory(MUTATION_CATEGORY);
		addCropSticks(registry, MUTATION_CATEGORY);

		EmiStack normalSeed = EmiStack.of(ModItems.SEED.get()).comparison(Comparison.compareNbt());
		registry.removeEmiStacks(normalSeed);
		AgriRecipeDisplays.mutations().forEach(mutation -> registry.addRecipe(new CropMutationRecipe(mutation)));

		registry.addCategory(PRODUCE_CATEGORY);
		addCropSticks(registry, PRODUCE_CATEGORY);
		AgriRecipeDisplays.produce().forEach(display -> registry.addRecipe(new CropProduceRecipe(display)));

		registry.addCategory(CLIPPING_CATEGORY);
		registry.addWorkstation(CLIPPING_CATEGORY, CLIPPER);
		AgriRecipeDisplays.clippings().forEach(display -> registry.addRecipe(new CropClippingRecipe(display)));

		registry.addCategory(REQUIREMENT_CATEGORY);
		registry.addWorkstation(REQUIREMENT_CATEGORY, FARMLAND);
		AgriRecipeDisplays.requirements().forEach(display -> registry.addRecipe(new CropRequirementRecipe(display)));
	}

	private static void addCropSticks(EmiRegistry registry, EmiRecipeCategory category) {
		for (ItemStack sticks : AgriRecipeDisplays.cropSticks()) {
			registry.addWorkstation(category, EmiStack.of(sticks));
		}
	}

}
