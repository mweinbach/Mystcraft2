package com.agricraft.agricraft.compat.jei;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.api.genetic.AgriGenome;
import com.agricraft.agricraft.api.plant.AgriPlant;
import com.agricraft.agricraft.client.gui.AgriGuiGraphics;
import com.agricraft.agricraft.common.item.AgriSeedItem;
import com.agricraft.agricraft.common.item.crafting.MagnifyingHelmetRecipe;
import com.agricraft.agricraft.common.registry.ModItems;
import com.agricraft.agricraft.compat.AgriRecipeDisplays;
import com.agricraft.agricraft.compat.RecipeViewers;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * JEI plugin: the produce, clipping, mutation and growth requirement categories, the plant ingredient type and
 * the magnifying helmet crafting recipe. Ported from AgriCraft 4.0.6 to JEI 11. Forge finds it by its
 * {@link JeiPlugin} annotation, Fabric by the {@code jei_mod_plugin} entrypoint. It stays quiet when EMI is
 * installed (EMI shows JEI plugins' categories itself, so they would be listed twice).
 * <p>
 * Port addition: JEI 11 has no public reload, so when a remote server's plants arrive after JEI started
 * ({@link RecipeViewers#reloadAfterRemoteData()}), {@link #addMissingDisplays()} adds the new recipes, plants
 * and seeds through JEI's runtime API.
 */
@JeiPlugin
public class AgriCraftJeiPlugin implements IModPlugin {

	public static final ResourceLocation ID = new ResourceLocation(AgriApi.MOD_ID, "compat_jei");
	public static final RecipeType<AgriRecipeDisplays.PlantDisplay> PRODUCE = new RecipeType<>(new ResourceLocation(AgriApi.MOD_ID, "jei/produce"), AgriRecipeDisplays.PlantDisplay.class);
	public static final RecipeType<AgriRecipeDisplays.PlantDisplay> CLIPPING = new RecipeType<>(new ResourceLocation(AgriApi.MOD_ID, "jei/clipping"), AgriRecipeDisplays.PlantDisplay.class);
	public static final RecipeType<AgriRecipeDisplays.Mutation> MUTATION = new RecipeType<>(new ResourceLocation(AgriApi.MOD_ID, "jei/mutation"), AgriRecipeDisplays.Mutation.class);
	public static final RecipeType<AgriRecipeDisplays.PlantDisplay> REQUIREMENT = new RecipeType<>(new ResourceLocation(AgriApi.MOD_ID, "jei/requirement"), AgriRecipeDisplays.PlantDisplay.class);

	private static volatile IJeiRuntime runtime;
	/** The recipes (by display id) and plants JEI already has, so runtime additions don't duplicate them. */
	private static final Set<ResourceLocation> SHOWN_RECIPES = new HashSet<>();
	private static final Set<ResourceLocation> SHOWN_PLANTS = new HashSet<>();

	@Override
	@NotNull
	public ResourceLocation getPluginUid() {
		return ID;
	}

	@Override
	public void registerItemSubtypes(ISubtypeRegistration registration) {
		// Register all The Seeds.
		registration.registerSubtypeInterpreter(ModItems.SEED.get(), (stack, context) -> {
			AgriGenome genome = stack.getTag() == null ? null : AgriGenome.fromNBT(stack.getTag());
			if (genome != null) {
				return genome.getSpeciesGene().getTrait();
			}
			return "agricraft:unknown";
		});
	}

	@Override
	public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
		if (RecipeViewers.emi()) {
			return;
		}
		registration.getCraftingCategory().addCategoryExtension(MagnifyingHelmetRecipe.class, (recipe) -> new MagnifyingHelmetExtension());
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		if (RecipeViewers.emi()) {
			return;
		}
		registration.addRecipeCategories(new CropProduceCategory());
		registration.addRecipeCategories(new CropClippingCategory());
		registration.addRecipeCategories(new CropMutationCategory());
		registration.addRecipeCategories(new CropRequirementCategory(registration.getJeiHelpers().getGuiHelper()));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		synchronized (SHOWN_RECIPES) {
			SHOWN_RECIPES.clear();
			if (RecipeViewers.emi()) {
				return;
			}
			registration.addRecipes(PRODUCE, unseen(AgriRecipeDisplays.produce(), AgriRecipeDisplays.PlantDisplay::id));
			registration.addRecipes(CLIPPING, unseen(AgriRecipeDisplays.clippings(), AgriRecipeDisplays.PlantDisplay::id));
			registration.addRecipes(REQUIREMENT, unseen(AgriRecipeDisplays.requirements(), AgriRecipeDisplays.PlantDisplay::id));
			registration.addRecipes(MUTATION, unseen(AgriRecipeDisplays.mutations(), AgriRecipeDisplays.Mutation::id));
		}
	}

	@Override
	public void registerIngredients(IModIngredientRegistration registration) {
		synchronized (SHOWN_RECIPES) {
			SHOWN_PLANTS.clear();
			if (RecipeViewers.emi()) {
				return;
			}
			// registered even when no plants are loaded yet, so plants added at runtime have a type
			registration.register(PlantIngredient.TYPE, unseenPlants(), PlantIngredient.HELPER, PlantIngredient.RENDERER);
		}
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		if (RecipeViewers.emi()) {
			return;
		}
		registration.addRecipeCatalyst(AgriRecipeDisplays.clipper(), CLIPPING);
		for (ItemStack sticks : AgriRecipeDisplays.cropSticks()) {
			registration.addRecipeCatalyst(sticks, PRODUCE, CLIPPING, MUTATION, REQUIREMENT);
		}
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;
	}

	@Override
	public void onRuntimeUnavailable() {
		runtime = null;
	}

	/** Adds the recipes, plants and seeds that arrived from a remote server after JEI started. */
	public static void addMissingDisplays() {
		IJeiRuntime jeiRuntime = runtime;
		if (jeiRuntime == null || RecipeViewers.emi()) {
			return;
		}
		synchronized (SHOWN_RECIPES) {
			IRecipeManager recipes = jeiRuntime.getRecipeManager();
			add(recipes, PRODUCE, unseen(AgriRecipeDisplays.produce(), AgriRecipeDisplays.PlantDisplay::id));
			add(recipes, CLIPPING, unseen(AgriRecipeDisplays.clippings(), AgriRecipeDisplays.PlantDisplay::id));
			add(recipes, REQUIREMENT, unseen(AgriRecipeDisplays.requirements(), AgriRecipeDisplays.PlantDisplay::id));
			add(recipes, MUTATION, unseen(AgriRecipeDisplays.mutations(), AgriRecipeDisplays.Mutation::id));
			List<AgriPlant> plants = unseenPlants();
			if (!plants.isEmpty()) {
				var ingredients = jeiRuntime.getIngredientManager();
				ingredients.addIngredientsAtRuntime(PlantIngredient.TYPE, plants);
				// the creative seeds tab JEI read its item list from was empty before the plants arrived
				ingredients.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, plants.stream().map(AgriSeedItem::toStack).toList());
			}
		}
	}

	private static <T> void add(IRecipeManager manager, RecipeType<T> type, List<T> recipes) {
		if (!recipes.isEmpty()) {
			manager.addRecipes(type, recipes);
		}
	}

	private static <T> List<T> unseen(List<T> displays, Function<T, ResourceLocation> id) {
		return displays.stream().filter(display -> SHOWN_RECIPES.add(id.apply(display))).toList();
	}

	private static List<AgriPlant> unseenPlants() {
		return AgriApi.getPlantRegistry().map(registry -> registry.keySet().stream().sorted()
				.filter(id -> AgriRecipeDisplays.showsPlant(registry.get(id)))
				.filter(SHOWN_PLANTS::add).map(registry::get).toList()).orElse(List.of());
	}

	public static IDrawable createDrawable(ResourceLocation location, int uOffset, int vOffset, int width, int height, int textureWidth, int textureHeight) {
		return new IDrawable() {
			@Override
			public int getWidth() {
				return width;
			}

			@Override
			public int getHeight() {
				return height;
			}

			@Override
			public void draw(PoseStack poseStack, int xOffset, int yOffset) {
				new AgriGuiGraphics(poseStack).blit(location, xOffset, yOffset, uOffset, vOffset, getWidth(), getHeight(), textureWidth, textureHeight);
			}
		};
	}

}
