package ports.gridwright.registry;

import ports.gridwright.Gridwright;
import ports.gridwright.platform.Platform;
import ports.gridwright.recipe.ResonatingRecipe;
import net.minecraft.core.Registry;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.function.Supplier;

public final class ModRecipes {

    public static final Supplier<RecipeType<ResonatingRecipe>> RESONATING = Platform.register(Registry.RECIPE_TYPE_REGISTRY,
            "resonating", () -> new RecipeType<ResonatingRecipe>() {
                @Override
                public String toString() {
                    return Gridwright.MOD_ID + ":resonating";
                }
            });

    public static final Supplier<RecipeSerializer<ResonatingRecipe>> RESONATING_SERIALIZER = Platform.register(
            Registry.RECIPE_SERIALIZER_REGISTRY, "resonating", ResonatingRecipe.Serializer::new);

    private ModRecipes() { }

    public static void init() { }
}
