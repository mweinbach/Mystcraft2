package com.technovision.advancedgenetics.registry;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.common.recipe.cellanalyzer.CellAnalyzerRecipe;
import com.technovision.advancedgenetics.common.recipe.cellanalyzer.CellAnalyzerRecipeSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class RecipeRegistry {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, AdvancedGenetics.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, AdvancedGenetics.MOD_ID);

    // Cell Analyzer
    public static final RegistryObject<CellAnalyzerRecipeSerializer> CELL_ANALYZER_SERIALIZER = SERIALIZERS.register(CellAnalyzerRecipe.ID, CellAnalyzerRecipeSerializer::new);
    public static final RegistryObject<RecipeType<CellAnalyzerRecipe>> CELL_ANALYZER_TYPE = TYPES.register(CellAnalyzerRecipe.ID,
            () -> RecipeType.simple(new ResourceLocation(AdvancedGenetics.MOD_ID, CellAnalyzerRecipe.ID)));
}
