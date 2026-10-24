package ports.gridwright.compat.rei;

import ports.gridwright.compat.ResonatingDisplays;
import ports.gridwright.recipe.ResonatingRecipe;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.util.EntryIngredients;

import java.util.List;
import java.util.Optional;

/** One resonating recipe as an REI display. Only REI's common API, so it also loads on servers. */
public class ResonatingReiDisplay extends BasicDisplay {

    public static final CategoryIdentifier<ResonatingReiDisplay> CATEGORY = CategoryIdentifier.of(ResonatingDisplays.CATEGORY);

    private final ResonatingRecipe recipe;

    public ResonatingReiDisplay(ResonatingRecipe recipe) {
        super(List.of(EntryIngredients.ofItemStacks(ResonatingDisplays.inputs(recipe))),
                List.of(EntryIngredients.ofItemStacks(List.of(ResonatingDisplays.output(recipe)))),
                Optional.of(recipe.getId()));
        this.recipe = recipe;
    }

    public ResonatingRecipe recipe() {
        return recipe;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return CATEGORY;
    }
}
