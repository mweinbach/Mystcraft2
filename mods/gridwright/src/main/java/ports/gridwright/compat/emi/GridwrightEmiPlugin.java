package ports.gridwright.compat.emi;

import ports.gridwright.compat.ResonatingDisplays;
import ports.gridwright.recipe.ResonatingRecipe;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;

/**
 * EMI plugin: the resonating category. Forge finds it by its {@link EmiEntrypoint} annotation,
 * Fabric by the {@code emi} entrypoint.
 */
@EmiEntrypoint
public class GridwrightEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        EmiRecipeCategory category = category();
        registry.addCategory(category);
        registry.addWorkstation(category, EmiStack.of(ResonatingDisplays.workstation()));
        for (ResonatingRecipe recipe : ResonatingDisplays.recipes(registry.getRecipeManager())) {
            registry.addRecipe(new ResonatingEmiRecipe(category, recipe));
        }
    }

    /** The category, titled by {@code emi.category.gridwright.resonating}. */
    public static EmiRecipeCategory category() {
        return new EmiRecipeCategory(ResonatingDisplays.CATEGORY, EmiStack.of(ResonatingDisplays.workstation()));
    }
}
