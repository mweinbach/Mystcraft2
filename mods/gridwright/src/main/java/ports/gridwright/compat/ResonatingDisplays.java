package ports.gridwright.compat;

import ports.gridwright.Gridwright;
import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.recipe.ResonatingRecipe;
import ports.gridwright.registry.ModBlocks;
import ports.gridwright.registry.ModRecipes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What every recipe viewer shows for resonating, independent of JEI, REI and EMI, so the three
 * plugins (and the GameTests) agree. Never touches a viewer class.
 */
public final class ResonatingDisplays {

    public static final ResourceLocation CATEGORY = new ResourceLocation(Gridwright.MOD_ID, "resonating");
    public static final String TITLE_KEY = "gui.gridwright.category.resonating";
    public static final int WIDTH = 120;
    public static final int HEIGHT = 36;
    public static final int INPUT_X = 4;
    public static final int OUTPUT_X = 64;
    public static final int SLOT_Y = 4;
    public static final int ARROW_X = 28;
    public static final int TEXT_Y = 26;

    private ResonatingDisplays() { }

    public static Component title() {
        return Component.translatable(TITLE_KEY);
    }

    /** Every resonating recipe, sorted by ID so all viewers list them in the same order. */
    public static List<ResonatingRecipe> recipes(RecipeManager manager) {
        List<ResonatingRecipe> recipes = new ArrayList<>(manager.getAllRecipesFor(ModRecipes.RESONATING.get()));
        recipes.sort(Comparator.comparing(recipe -> recipe.getId().toString()));
        return List.copyOf(recipes);
    }

    /** The input stacks, each with the recipe's amount. */
    public static List<ItemStack> inputs(ResonatingRecipe recipe) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : recipe.ingredient().getItems()) {
            ItemStack copy = stack.copy();
            copy.setCount(recipe.amount());
            stacks.add(copy);
        }
        return stacks;
    }

    public static ItemStack output(ResonatingRecipe recipe) {
        return recipe.output().copy();
    }

    /** The block that runs these recipes. */
    public static ItemStack workstation() {
        return new ItemStack(ModBlocks.RESONATOR.get());
    }

    /** "4 GP, 5.0 s" under the slots. */
    public static Component details(ResonatingRecipe recipe) {
        return Component.translatable("gui.gridwright.category.resonating.details", GridBlockEntity.format(recipe.gp()),
                String.format(java.util.Locale.ROOT, "%.1f", recipe.time() / 20.0));
    }
}
