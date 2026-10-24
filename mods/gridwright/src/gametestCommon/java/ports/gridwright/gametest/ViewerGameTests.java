package ports.gridwright.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import ports.gridwright.compat.ResonatingDisplays;
import ports.gridwright.compat.Viewers;
import ports.gridwright.recipe.ResonatingRecipe;
import ports.gridwright.registry.ModBlocks;
import ports.gridwright.registry.ModRecipes;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;

/**
 * The resonating category the JEI, REI and EMI plugins show. The plugins' screens are client-only, so
 * these tests check what a dedicated server can: the shared display data, that each viewer is present
 * exactly when its {@code -PwithJei}/{@code -PwithRei}/{@code -PwithEmi} flag asked for it (they are
 * compile-only otherwise), and, with a viewer installed, that its plugin is wired up (JEI's recipe type,
 * REI's displays mirroring the recipes, and EMI's plugin and recipe classes).
 * Viewer types are touched only from the nested classes, which load only when the viewer is present.
 */
public class ViewerGameTests {

    /** The viewers this run was started with ("none" or a comma-separated list), set by the build. */
    private static Set<String> expectedViewers() {
        String flags = System.getProperty("gridwright.viewers", "none");
        return Arrays.stream(flags.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty() && !s.equals("none")).collect(Collectors.toSet());
    }

    private static List<ResonatingRecipe> recipes(GameTestHelper helper) {
        return ResonatingDisplays.recipes(helper.getLevel().getRecipeManager());
    }

    @GameTest(template = TEMPLATE)
    public static void resonatingDisplaysCoverEveryRecipe(GameTestHelper helper) {
        List<ResonatingRecipe> all = helper.getLevel().getRecipeManager().getAllRecipesFor(ModRecipes.RESONATING.get());
        List<ResonatingRecipe> shown = recipes(helper);
        check(!shown.isEmpty() && shown.size() == all.size() && shown.containsAll(all), "Displays " + shown.size() + " of " + all.size());
        for (int i = 1; i < shown.size(); i++) {
            check(shown.get(i - 1).getId().compareTo(shown.get(i).getId()) < 0, "Displays are not sorted by ID");
        }
        for (ResonatingRecipe recipe : shown) {
            List<ItemStack> inputs = ResonatingDisplays.inputs(recipe);
            check(!inputs.isEmpty(), "No input shown for " + recipe.getId());
            for (ItemStack input : inputs) {
                check(recipe.accepts(input) && input.getCount() == recipe.amount(), "Input " + input + " for " + recipe.getId());
            }
            check(ItemStack.matches(ResonatingDisplays.output(recipe), recipe.output()), "Output for " + recipe.getId());
            String details = ResonatingDisplays.details(recipe).getString();
            check(details.contains("GP"), "Details text " + details);
        }
        check(ResonatingDisplays.workstation().is(ModBlocks.RESONATOR.get().asItem()), "The resonator is the workstation");
        check(ResonatingDisplays.CATEGORY.toString().equals("gridwright:resonating"), "Category " + ResonatingDisplays.CATEGORY);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void viewerPluginsFollowInstalledViewers(GameTestHelper helper) {
        Set<String> expected = expectedViewers();
        String[][] viewers = {{"jei", Viewers.JEI_API}, {"emi", Viewers.EMI_API}, {"rei", Viewers.REI_API}};
        for (String[] viewer : viewers) {
            boolean present = Viewers.present(viewer[1]);
            check(present == expected.contains(viewer[0]), viewer[0] + (present ? " is installed but was not asked for"
                    + " (viewer APIs must stay compile-only)" : " was asked for but is not installed"));
        }
        for (String plugin : List.of("compat/jei/GridwrightJeiPlugin", "compat/emi/GridwrightEmiPlugin", "compat/rei/GridwrightReiPlugin")) {
            check(ViewerGameTests.class.getClassLoader().getResource("ports/gridwright/" + plugin + ".class") != null,
                    plugin + " is not packaged");
        }
        List<ResonatingRecipe> recipes = recipes(helper);
        if (expected.contains("jei")) JeiCheck.run();
        if (expected.contains("emi")) EmiCheck.run(recipes);
        if (expected.contains("rei")) ReiCheck.run(recipes);
        helper.succeed();
    }

    /** Loaded only with JEI installed. */
    private static final class JeiCheck {
        static void run() {
            var plugin = new ports.gridwright.compat.jei.GridwrightJeiPlugin();
            check(plugin.getPluginUid().toString().equals("gridwright:jei"), "JEI plugin UID " + plugin.getPluginUid());
            var type = ports.gridwright.compat.jei.GridwrightJeiPlugin.RESONATING;
            check(type.getUid().equals(ResonatingDisplays.CATEGORY) && type.getRecipeClass() == ResonatingRecipe.class,
                    "JEI recipe type " + type.getUid());
        }
    }

    /**
     * Loaded only with EMI installed. EMI's item stacks ({@code ItemEmiStack}) refer to client-only
     * classes, so a dedicated server cannot build the category or recipe objects; this checks the
     * wiring instead. The recipe data itself is the shared {@link ResonatingDisplays} list tested above.
     */
    private static final class EmiCheck {
        static void run(List<ResonatingRecipe> recipes) {
            check(dev.emi.emi.api.EmiPlugin.class.isAssignableFrom(ports.gridwright.compat.emi.GridwrightEmiPlugin.class),
                    "The EMI plugin does not implement EmiPlugin");
            check(dev.emi.emi.api.recipe.EmiRecipe.class.isAssignableFrom(ports.gridwright.compat.emi.ResonatingEmiRecipe.class),
                    "The EMI recipe does not implement EmiRecipe");
            check(!recipes.isEmpty(), "No resonating recipes for EMI to show");
        }
    }

    /** Loaded only with REI installed; uses REI's common display API only. */
    private static final class ReiCheck {
        static void run(List<ResonatingRecipe> recipes) {
            for (ResonatingRecipe recipe : recipes) {
                var display = new ports.gridwright.compat.rei.ResonatingReiDisplay(recipe);
                check(display.getCategoryIdentifier().getIdentifier().equals(ResonatingDisplays.CATEGORY), "REI category");
                check(display.getDisplayLocation().orElseThrow().equals(recipe.getId()), "REI display ID for " + recipe.getId());
                check(display.getInputEntries().size() == 1 && display.getOutputEntries().size() == 1, "REI slots for " + recipe.getId());
                List<ItemStack> inputs = ResonatingDisplays.inputs(recipe);
                check(display.getInputEntries().get(0).size() == inputs.size(), "REI input alternatives for " + recipe.getId());
                ItemStack first = (ItemStack) display.getInputEntries().get(0).get(0).getValue();
                check(ItemStack.matches(first, inputs.get(0)), "REI input " + first + " for " + recipe.getId());
                ItemStack output = (ItemStack) display.getOutputEntries().get(0).get(0).getValue();
                check(ItemStack.matches(output, recipe.output()), "REI output for " + recipe.getId());
            }
        }
    }
}
