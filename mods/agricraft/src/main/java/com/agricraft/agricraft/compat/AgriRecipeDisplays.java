package com.agricraft.agricraft.compat;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.api.codecs.AgriMutation;
import com.agricraft.agricraft.api.plant.AgriPlant;
import com.agricraft.agricraft.common.item.AgriSeedItem;
import com.agricraft.agricraft.common.registry.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The recipe data AgriCraft's EMI, JEI and REI plugins show, built from the plant and mutation registries.
 * Port-specific: upstream AgriCraft 4 builds it in each plugin. Only common classes are used, so it also
 * works (and is tested) on a dedicated server and without any viewer installed.
 */
public final class AgriRecipeDisplays {

	/** Category ids, also EMI's category ids (titled by {@code emi.category.agricraft.*}). */
	public static final ResourceLocation MUTATION = new ResourceLocation(AgriApi.MOD_ID, "mutation");
	public static final ResourceLocation PRODUCE = new ResourceLocation(AgriApi.MOD_ID, "produce");
	public static final ResourceLocation CLIPPING = new ResourceLocation(AgriApi.MOD_ID, "clipping");
	public static final ResourceLocation REQUIREMENT = new ResourceLocation(AgriApi.MOD_ID, "requirement");

	public static final ResourceLocation MUTATION_TEXTURE = new ResourceLocation(AgriApi.MOD_ID, "textures/gui/jei/crop_mutation.png");
	public static final ResourceLocation PRODUCE_TEXTURE = new ResourceLocation(AgriApi.MOD_ID, "textures/gui/jei/crop_produce.png");

	private AgriRecipeDisplays() {
	}

	/** A mutation: two parent seeds give a child seed. {@code id} is a synthetic recipe id. */
	public record Mutation(ResourceLocation id, ResourceLocation mutationId, AgriPlant parent1, AgriPlant parent2, AgriPlant child,
	                       ItemStack parent1Seed, ItemStack parent2Seed, ItemStack childSeed) {
	}

	/** A plant's seed and what it gives (products, clip products, or nothing for requirements). */
	public record PlantDisplay(ResourceLocation id, ResourceLocation plantId, AgriPlant plant, ItemStack seed, List<ItemStack> outputs) {
	}

	/**
	 * Whether the viewers list this plant: not when a required product resolves to no item (e.g. Petinia's tin
	 * nuggets with no tin mod loaded). Such plants are hidden from the creative tab too and never come out of a
	 * mutation.
	 */
	public static boolean showsPlant(AgriPlant plant) {
		return plant != null && !plant.hasUnresolvedRequiredProducts();
	}

	/** Whether the viewers list this mutation: all three plants are loaded and none of them is hidden. */
	public static boolean showsMutation(AgriMutation mutation) {
		return mutation.isValid() && showsPlant(mutation.getParent1().orElseThrow()) && showsPlant(mutation.getParent2().orElseThrow())
				&& showsPlant(mutation.getChild().orElseThrow());
	}

	/** Every valid mutation (all three plants loaded and shown), sorted by mutation id. */
	public static List<Mutation> mutations() {
		List<Mutation> list = new ArrayList<>();
		AgriApi.getMutationRegistry().ifPresent(registry -> sorted(registry, (id, mutation) -> {
			if (!showsMutation(mutation)) {
				return;
			}
			AgriPlant parent1 = mutation.getParent1().orElseThrow();
			AgriPlant parent2 = mutation.getParent2().orElseThrow();
			AgriPlant child = mutation.getChild().orElseThrow();
			list.add(new Mutation(prefixedId(id, "mutations"), id, parent1, parent2, child,
					AgriSeedItem.toStack(parent1), AgriSeedItem.toStack(parent2), AgriSeedItem.toStack(child)));
		}));
		return list;
	}

	/** One display per plant with at least one possible product. */
	public static List<PlantDisplay> produce() {
		return plants("products", (plant, out) -> plant.getAllPossibleProducts(out::add), true);
	}

	/** One display per plant with at least one possible clip product. */
	public static List<PlantDisplay> clippings() {
		return plants("clippings", (plant, out) -> plant.getAllPossibleClipProducts(out::add), true);
	}

	/** One display per plant, for the growth requirement category (no outputs). */
	public static List<PlantDisplay> requirements() {
		return plants("requirements", (plant, out) -> { }, false);
	}

	/** The default seed of every shown plant, sorted by plant id. */
	public static List<ItemStack> seeds() {
		List<ItemStack> list = new ArrayList<>();
		AgriApi.getPlantRegistry().ifPresent(registry -> sorted(registry, (id, plant) -> {
			if (showsPlant(plant)) {
				list.add(AgriSeedItem.toStack(plant));
			}
		}));
		return list;
	}

	/** The crop sticks, the workstations of the mutation, produce and requirement categories. */
	public static List<ItemStack> cropSticks() {
		return List.of(new ItemStack(ModItems.WOODEN_CROP_STICKS.get()), new ItemStack(ModItems.IRON_CROP_STICKS.get()),
				new ItemStack(ModItems.OBSIDIAN_CROP_STICKS.get()));
	}

	public static ItemStack clipper() {
		return new ItemStack(ModItems.CLIPPER.get());
	}

	public static ItemStack farmland() {
		return new ItemStack(Items.FARMLAND);
	}

	/** The key seeds are compared by: their species, as upstream's EMI comparison and JEI subtype interpreter. */
	public static String seedSpecies(ItemStack stack) {
		return AgriSeedItem.getSpecies(stack);
	}

	/** Upstream's synthetic recipe ids, e.g. {@code agricraft:/mutations/agricraft/carrot}. */
	public static ResourceLocation prefixedId(ResourceLocation key, String prefix) {
		return new ResourceLocation(AgriApi.MOD_ID, "/" + prefix + "/" + key.toString().replace(":", "/"));
	}

	private interface Outputs {
		void collect(AgriPlant plant, List<ItemStack> out);
	}

	private static List<PlantDisplay> plants(String prefix, Outputs outputs, boolean needOutputs) {
		List<PlantDisplay> list = new ArrayList<>();
		AgriApi.getPlantRegistry().ifPresent(registry -> sorted(registry, (id, plant) -> {
			if (!showsPlant(plant)) {
				return;
			}
			List<ItemStack> out = new ArrayList<>();
			outputs.collect(plant, out);
			if (!needOutputs || !out.isEmpty()) {
				list.add(new PlantDisplay(prefixedId(id, prefix), id, plant, AgriSeedItem.toStack(plant), List.copyOf(out)));
			}
		}));
		return list;
	}

	private static <T> void sorted(Registry<T> registry, BiConsumer<ResourceLocation, T> action) {
		registry.keySet().stream().sorted().forEach(id -> action.accept(id, registry.get(id)));
	}

}
