package com.technovision.advancedgenetics.registry;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.common.recipe.cellanalyzer.CellAnalyzerRecipe;
import com.technovision.advancedgenetics.common.recipe.cellanalyzer.CellAnalyzerRecipeSerializer;
import com.technovision.advancedgenetics.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.function.Supplier;

public class RecipeRegistry {

    // Cell Analyzer
    public static final Supplier<CellAnalyzerRecipeSerializer> CELL_ANALYZER_SERIALIZER = Platform.register(Registry.RECIPE_SERIALIZER_REGISTRY,
            CellAnalyzerRecipe.ID, CellAnalyzerRecipeSerializer::new);
    public static final Supplier<RecipeType<CellAnalyzerRecipe>> CELL_ANALYZER_TYPE = Platform.register(Registry.RECIPE_TYPE_REGISTRY,
            CellAnalyzerRecipe.ID, () -> simpleType(new ResourceLocation(AdvancedGenetics.MOD_ID, CellAnalyzerRecipe.ID)));

    public static void init() { }

    private static <T extends Recipe<?>> RecipeType<T> simpleType(ResourceLocation id) {
        return new RecipeType<>() {
            @Override
            public String toString() {
                return id.toString();
            }
        };
    }
}
