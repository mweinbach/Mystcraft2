package ports.gridwright.compat.rei;

import ports.gridwright.compat.ResonatingDisplays;
import ports.gridwright.recipe.ResonatingRecipe;
import ports.gridwright.registry.ModRecipes;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.util.EntryIngredients;

import java.util.List;

/**
 * REI client plugin: the resonating category. Fabric finds it by the {@code rei_client} entrypoint;
 * on Forge an annotated subclass in the Forge sources registers it.
 */
public class GridwrightReiPlugin implements REIClientPlugin {

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new ResonatingReiCategory());
        registry.addWorkstations(ResonatingReiDisplay.CATEGORY, EntryIngredients.ofItemStacks(List.of(ResonatingDisplays.workstation())));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        registry.registerRecipeFiller(ResonatingRecipe.class, ModRecipes.RESONATING.get(), ResonatingReiDisplay::new);
    }
}
