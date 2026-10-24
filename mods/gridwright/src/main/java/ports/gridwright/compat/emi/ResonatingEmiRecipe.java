package ports.gridwright.compat.emi;

import ports.gridwright.compat.ResonatingDisplays;
import ports.gridwright.recipe.ResonatingRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** One resonating recipe in EMI: input, a filling arrow timed to the recipe, output, GP and time. */
public class ResonatingEmiRecipe implements EmiRecipe {

    private static final int TEXT = 0x404040;

    private final EmiRecipeCategory category;
    private final ResonatingRecipe recipe;
    private final EmiIngredient input;
    private final EmiStack output;

    public ResonatingEmiRecipe(EmiRecipeCategory category, ResonatingRecipe recipe) {
        this.category = category;
        this.recipe = recipe;
        this.input = EmiIngredient.of(recipe.ingredient(), recipe.amount());
        this.output = EmiStack.of(ResonatingDisplays.output(recipe));
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return category;
    }

    @Override
    public ResourceLocation getId() {
        return recipe.getId();
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return List.of(input);
    }

    @Override
    public List<EmiStack> getOutputs() {
        return List.of(output);
    }

    @Override
    public int getDisplayWidth() {
        return ResonatingDisplays.WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return ResonatingDisplays.HEIGHT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(input, ResonatingDisplays.INPUT_X, ResonatingDisplays.SLOT_Y);
        widgets.addFillingArrow(ResonatingDisplays.ARROW_X, ResonatingDisplays.SLOT_Y + 1, recipe.time() * 50);
        widgets.addSlot(output, ResonatingDisplays.OUTPUT_X, ResonatingDisplays.SLOT_Y).recipeContext(this);
        widgets.addText(ResonatingDisplays.details(recipe), ResonatingDisplays.INPUT_X, ResonatingDisplays.TEXT_Y, TEXT, false);
    }
}
