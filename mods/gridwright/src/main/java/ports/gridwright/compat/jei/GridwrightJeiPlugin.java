package ports.gridwright.compat.jei;

import ports.gridwright.Gridwright;
import ports.gridwright.compat.ClientRecipes;
import ports.gridwright.compat.ResonatingDisplays;
import ports.gridwright.compat.Viewers;
import ports.gridwright.recipe.ResonatingRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * JEI plugin: the resonating category. Forge finds it by its {@link JeiPlugin} annotation, Fabric by
 * the {@code jei_mod_plugin} entrypoint. When EMI is also installed it registers nothing, since EMI
 * already shows Gridwright's own EMI category.
 */
@JeiPlugin
public class GridwrightJeiPlugin implements IModPlugin {

    public static final ResourceLocation UID = new ResourceLocation(Gridwright.MOD_ID, "jei");
    public static final RecipeType<ResonatingRecipe> RESONATING = new RecipeType<>(ResonatingDisplays.CATEGORY, ResonatingRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (Viewers.emi()) return;
        registration.addRecipeCategories(new ResonatingJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        RecipeManager manager = ClientRecipes.manager();
        if (Viewers.emi() || manager == null) return;
        registration.addRecipes(RESONATING, ResonatingDisplays.recipes(manager));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (Viewers.emi()) return;
        registration.addRecipeCatalyst(ResonatingDisplays.workstation(), RESONATING);
    }
}
