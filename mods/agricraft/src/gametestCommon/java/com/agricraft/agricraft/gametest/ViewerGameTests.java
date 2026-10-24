package com.agricraft.agricraft.gametest;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.api.codecs.AgriMutation;
import com.agricraft.agricraft.api.plant.AgriPlant;
import com.agricraft.agricraft.common.item.AgriSeedItem;
import com.agricraft.agricraft.common.registry.ModItems;
import com.agricraft.agricraft.compat.AgriRecipeDisplays;
import com.agricraft.agricraft.compat.RecipeViewers;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agricraft.agricraft.gametest.GameTestSupport.TEMPLATE;
import static com.agricraft.agricraft.gametest.GameTestSupport.check;

/**
 * The recipe-viewer plugins (EMI, JEI, REI). Their screens are client-only, so these tests check what a
 * dedicated server can: the shared {@link AgriRecipeDisplays} data all three plugins show, that each viewer is
 * present exactly when its {@code -PwithJei}/{@code -PwithEmi}/{@code -PwithRei} flag asked for it (they are
 * compile-only otherwise), and, with a viewer installed, that its plugin is wired up. Viewer types are touched
 * only from the nested classes, which load only when the viewer is present.
 */
public class ViewerGameTests {

	/** The viewers this run was started with ("none" or a comma-separated list), set by the build. */
	private static Set<String> expectedViewers() {
		String flags = System.getProperty("agricraft.viewers", "none");
		return Arrays.stream(flags.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT))
				.filter(s -> !s.isEmpty() && !s.equals("none")).collect(Collectors.toSet());
	}

	private static String species(ItemStack seed) {
		check(seed.is(ModItems.SEED.get()), seed + " is not an AgriCraft seed");
		return AgriSeedItem.getSpecies(seed);
	}

	private static <T> void checkSorted(List<T> displays, Function<T, ResourceLocation> id, String what) {
		for (int i = 1; i < displays.size(); i++) {
			check(id.apply(displays.get(i - 1)).compareTo(id.apply(displays.get(i))) < 0, what + " displays are not sorted by id");
		}
	}

	@GameTest(template = TEMPLATE)
	public static void mutationDisplaysMatchTheMutationRegistry(GameTestHelper helper) {
		Registry<AgriMutation> registry = AgriApi.getMutationRegistry().orElseThrow();
		List<AgriRecipeDisplays.Mutation> shown = AgriRecipeDisplays.mutations();
		long valid = registry.stream().filter(AgriMutation::isValid).count();
		check(!shown.isEmpty() && shown.size() == valid, "Mutation displays " + shown.size() + " of " + valid + " valid mutations");
		checkSorted(shown, AgriRecipeDisplays.Mutation::mutationId, "Mutation");
		for (AgriRecipeDisplays.Mutation display : shown) {
			AgriMutation mutation = registry.get(display.mutationId());
			check(mutation != null, "No mutation " + display.mutationId());
			check(species(display.parent1Seed()).equals(mutation.parent1().toString())
					&& species(display.parent2Seed()).equals(mutation.parent2().toString())
					&& species(display.childSeed()).equals(mutation.child().toString()), "Seeds of " + display.mutationId());
			check(display.child() == mutation.getChild().orElseThrow(), "Child plant of " + display.mutationId());
		}
		AgriRecipeDisplays.Mutation carrot = shown.stream()
				.filter(m -> m.mutationId().equals(new ResourceLocation("minecraft", "carrot"))).findFirst()
				.orElseThrow(() -> new IllegalStateException("The carrot mutation is not listed"));
		check(species(carrot.parent1Seed()).equals("minecraft:wheat") && species(carrot.parent2Seed()).equals("minecraft:potato")
				&& species(carrot.childSeed()).equals("minecraft:carrot"), "wheat + potato -> carrot seeds");
		check(carrot.id().toString().equals("agricraft:/mutations/minecraft/carrot"), "Carrot mutation recipe id " + carrot.id());
		check(ItemStack.matches(carrot.childSeed(), AgriSeedItem.toStack(AgriApi.getPlant("minecraft:carrot").orElseThrow())),
				"The carrot seed is the plant's default seed");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void plantDisplaysCoverEveryPlant(GameTestHelper helper) {
		Registry<AgriPlant> registry = AgriApi.getPlantRegistry().orElseThrow();
		checkOutputs(registry, AgriRecipeDisplays.produce(), AgriPlant::getAllPossibleProducts, "products");
		checkOutputs(registry, AgriRecipeDisplays.clippings(), AgriPlant::getAllPossibleClipProducts, "clippings");
		List<AgriRecipeDisplays.PlantDisplay> requirements = AgriRecipeDisplays.requirements();
		check(requirements.size() == registry.size(), "Requirement displays " + requirements.size() + " of " + registry.size() + " plants");
		for (AgriRecipeDisplays.PlantDisplay display : requirements) {
			check(display.outputs().isEmpty() && species(display.seed()).equals(display.plantId().toString()), "Requirement display " + display.id());
		}
		List<ItemStack> seeds = AgriRecipeDisplays.seeds();
		check(seeds.size() == registry.size(), "Seeds " + seeds.size() + " of " + registry.size() + " plants");
		check(seeds.stream().map(AgriSeedItem::getSpecies).distinct().count() == seeds.size(), "Duplicate seeds");
		check(AgriRecipeDisplays.produce().stream().anyMatch(d -> d.plantId().equals(new ResourceLocation("minecraft", "wheat"))
				&& d.outputs().stream().anyMatch(s -> s.is(Items.WHEAT))), "Wheat produces wheat");
		List<ItemStack> sticks = AgriRecipeDisplays.cropSticks();
		check(sticks.size() == 3 && sticks.get(0).is(ModItems.WOODEN_CROP_STICKS.get()) && sticks.get(1).is(ModItems.IRON_CROP_STICKS.get())
				&& sticks.get(2).is(ModItems.OBSIDIAN_CROP_STICKS.get()), "Crop sticks workstations");
		check(AgriRecipeDisplays.clipper().is(ModItems.CLIPPER.get()) && AgriRecipeDisplays.farmland().is(Items.FARMLAND), "Clipper and farmland workstations");
		check(List.of(AgriRecipeDisplays.MUTATION, AgriRecipeDisplays.PRODUCE, AgriRecipeDisplays.CLIPPING, AgriRecipeDisplays.REQUIREMENT).stream()
				.map(ResourceLocation::toString).toList().equals(List.of("agricraft:mutation", "agricraft:produce", "agricraft:clipping", "agricraft:requirement")),
				"Category ids");
		helper.succeed();
	}

	/** Every plant with outputs has exactly one display, with the seed and the plant's outputs. */
	private static void checkOutputs(Registry<AgriPlant> registry, List<AgriRecipeDisplays.PlantDisplay> shown,
	                                 BiConsumer<AgriPlant, java.util.function.Consumer<ItemStack>> outputs, String what) {
		checkSorted(shown, AgriRecipeDisplays.PlantDisplay::plantId, what);
		int expected = 0;
		for (ResourceLocation id : registry.keySet()) {
			AgriPlant plant = registry.get(id);
			List<ItemStack> list = new ArrayList<>();
			outputs.accept(plant, list::add);
			List<AgriRecipeDisplays.PlantDisplay> displays = shown.stream().filter(d -> d.plantId().equals(id)).toList();
			if (list.isEmpty()) {
				check(displays.isEmpty(), "Plant " + id + " has no " + what + " but a display");
				continue;
			}
			expected++;
			check(displays.size() == 1, "Plant " + id + " has " + displays.size() + " " + what + " displays");
			AgriRecipeDisplays.PlantDisplay display = displays.get(0);
			check(display.plant() == plant && species(display.seed()).equals(id.toString()), "Seed of the " + what + " display of " + id);
			check(display.id().toString().equals("agricraft:/" + what + "/" + id.getNamespace() + "/" + id.getPath()), "Display id " + display.id());
			check(display.outputs().size() == list.size(), "Output count of the " + what + " display of " + id);
			for (int i = 0; i < list.size(); i++) {
				check(ItemStack.matches(display.outputs().get(i), list.get(i)), "Output " + i + " of the " + what + " display of " + id);
			}
		}
		check(expected > 0 && shown.size() == expected, what + " displays " + shown.size() + " of " + expected);
	}

	@GameTest(template = TEMPLATE)
	public static void viewerPluginsFollowInstalledViewers(GameTestHelper helper) {
		Set<String> expected = expectedViewers();
		String[][] viewers = {{"jei", RecipeViewers.JEI_API}, {"emi", RecipeViewers.EMI_API}, {"rei", RecipeViewers.REI_API}};
		for (String[] viewer : viewers) {
			boolean present = RecipeViewers.present(viewer[1]);
			check(present == expected.contains(viewer[0]), viewer[0] + (present ? " is installed but was not asked for"
					+ " (viewer APIs must stay compile-only)" : " was asked for but is not installed"));
		}
		check(RecipeViewers.emi() == expected.contains("emi"), "RecipeViewers.emi()");
		for (String plugin : List.of("compat/jei/AgriCraftJeiPlugin", "compat/emi/AgriCraftEmiPlugin", "compat/rei/AgriCraftReiPlugin",
				"compat/jei/CropRequirementCategory", "compat/emi/CropRequirementRecipe", "compat/rei/AgriReiCategory", "compat/client/CropRequirementView")) {
			check(ViewerGameTests.class.getClassLoader().getResource("com/agricraft/agricraft/" + plugin + ".class") != null,
					plugin + " is not packaged");
		}
		if (expected.contains("jei")) JeiCheck.run();
		if (expected.contains("emi")) EmiCheck.run();
		if (expected.contains("rei")) ReiCheck.run();
		helper.succeed();
	}

	/** Loaded only with JEI installed. */
	private static final class JeiCheck {
		static void run() {
			var plugin = new com.agricraft.agricraft.compat.jei.AgriCraftJeiPlugin();
			check(plugin.getPluginUid().toString().equals("agricraft:compat_jei"), "JEI plugin UID " + plugin.getPluginUid());
			check(mezz.jei.api.IModPlugin.class.isInstance(plugin), "The JEI plugin does not implement IModPlugin");
			var mutation = com.agricraft.agricraft.compat.jei.AgriCraftJeiPlugin.MUTATION;
			check(mutation.getUid().toString().equals("agricraft:jei/mutation") && mutation.getRecipeClass() == AgriRecipeDisplays.Mutation.class,
					"JEI mutation type " + mutation.getUid());
			var types = List.of(com.agricraft.agricraft.compat.jei.AgriCraftJeiPlugin.PRODUCE, com.agricraft.agricraft.compat.jei.AgriCraftJeiPlugin.CLIPPING,
					com.agricraft.agricraft.compat.jei.AgriCraftJeiPlugin.REQUIREMENT);
			check(types.stream().map(t -> t.getUid().toString()).toList().equals(List.of("agricraft:jei/produce", "agricraft:jei/clipping", "agricraft:jei/requirement"))
					&& types.stream().allMatch(t -> t.getRecipeClass() == AgriRecipeDisplays.PlantDisplay.class), "JEI plant recipe types");
			// no JEI runtime on a server: the remote-data hook must be a no-op
			com.agricraft.agricraft.compat.jei.AgriCraftJeiPlugin.addMissingDisplays();
		}
	}

	/**
	 * Loaded only with EMI installed. EMI's item stacks refer to client-only classes, so a dedicated server
	 * cannot build the categories or recipes; this checks the wiring instead. The recipe data itself is the
	 * shared {@link AgriRecipeDisplays} tested above.
	 */
	private static final class EmiCheck {
		static void run() {
			check(dev.emi.emi.api.EmiPlugin.class.isAssignableFrom(com.agricraft.agricraft.compat.emi.AgriCraftEmiPlugin.class),
					"The EMI plugin does not implement EmiPlugin");
			for (Class<?> recipe : List.of(com.agricraft.agricraft.compat.emi.CropMutationRecipe.class, com.agricraft.agricraft.compat.emi.CropProduceRecipe.class,
					com.agricraft.agricraft.compat.emi.CropClippingRecipe.class, com.agricraft.agricraft.compat.emi.CropRequirementRecipe.class)) {
				check(dev.emi.emi.api.recipe.EmiRecipe.class.isAssignableFrom(recipe), recipe.getSimpleName() + " does not implement EmiRecipe");
			}
			check(classFileMentions(com.agricraft.agricraft.compat.emi.AgriCraftEmiPlugin.class, "Ldev/emi/emi/api/EmiEntrypoint;"),
					"The EMI plugin is not annotated with @EmiEntrypoint (Forge discovery)");
		}

		/** {@code @EmiEntrypoint} has class retention (Forge reads it from the scan data), so look in the class file. */
		private static boolean classFileMentions(Class<?> type, String descriptor) {
			try (var in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
				return in != null && new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1).contains(descriptor);
			} catch (java.io.IOException e) {
				return false;
			}
		}
	}

	/** Loaded only with REI installed; uses REI's common display API only. */
	private static final class ReiCheck {
		static void run() {
			for (AgriRecipeDisplays.Mutation mutation : AgriRecipeDisplays.mutations()) {
				var display = com.agricraft.agricraft.compat.rei.AgriReiDisplay.mutation(mutation);
				check(display.getCategoryIdentifier().getIdentifier().equals(AgriRecipeDisplays.MUTATION), "REI mutation category");
				check(display.getDisplayLocation().orElseThrow().equals(mutation.id()), "REI display id for " + mutation.id());
				check(display.getInputEntries().size() == 2 && display.getOutputEntries().size() == 1 && display.plants().size() == 3,
						"REI mutation slots for " + mutation.id());
				check(species((ItemStack) display.getInputEntries().get(0).get(0).getValue()).equals(species(mutation.parent1Seed()))
						&& species((ItemStack) display.getInputEntries().get(1).get(0).getValue()).equals(species(mutation.parent2Seed()))
						&& species((ItemStack) display.getOutputEntries().get(0).get(0).getValue()).equals(species(mutation.childSeed())),
						"REI mutation seeds for " + mutation.id());
			}
			checkPlants(AgriRecipeDisplays.produce(), com.agricraft.agricraft.compat.rei.AgriReiDisplay::produce, AgriRecipeDisplays.PRODUCE);
			checkPlants(AgriRecipeDisplays.clippings(), com.agricraft.agricraft.compat.rei.AgriReiDisplay::clipping, AgriRecipeDisplays.CLIPPING);
			checkPlants(AgriRecipeDisplays.requirements(), com.agricraft.agricraft.compat.rei.AgriReiDisplay::requirement, AgriRecipeDisplays.REQUIREMENT);
		}

		private static void checkPlants(List<AgriRecipeDisplays.PlantDisplay> displays,
		                                Function<AgriRecipeDisplays.PlantDisplay, com.agricraft.agricraft.compat.rei.AgriReiDisplay> factory, ResourceLocation category) {
			check(!displays.isEmpty(), "No " + category + " displays for REI");
			for (AgriRecipeDisplays.PlantDisplay plant : displays) {
				var display = factory.apply(plant);
				check(display.getCategoryIdentifier().getIdentifier().equals(category), "REI category " + category);
				check(display.getDisplayLocation().orElseThrow().equals(plant.id()), "REI display id for " + plant.id());
				check(display.getInputEntries().size() == 1 && display.getOutputEntries().size() == plant.outputs().size(), "REI slots for " + plant.id());
				check(species((ItemStack) display.getInputEntries().get(0).get(0).getValue()).equals(plant.plantId().toString()), "REI seed for " + plant.id());
				for (int i = 0; i < plant.outputs().size(); i++) {
					check(ItemStack.matches((ItemStack) display.getOutputEntries().get(i).get(0).getValue(), plant.outputs().get(i)), "REI output " + i + " for " + plant.id());
				}
			}
		}
	}

}
