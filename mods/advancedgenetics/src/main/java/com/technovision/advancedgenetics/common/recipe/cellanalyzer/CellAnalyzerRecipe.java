package com.technovision.advancedgenetics.common.recipe.cellanalyzer;

import com.technovision.advancedgenetics.api.recipe.AbstractGeneticsRecipe;
import com.technovision.advancedgenetics.registry.RecipeRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public class CellAnalyzerRecipe extends AbstractGeneticsRecipe {

    public static final String ID = "cell_analyzer";

    private final ItemStack input;
    private final ItemStack output;

    public CellAnalyzerRecipe(ResourceLocation id, ItemStack input, ItemStack output) {
        super(id);
        this.input = input;
        this.output = output;
    }

    @Override
    public boolean matches(SimpleContainer inventory, Level world) {
        return !world.isClientSide();
    }

    @Override
    public ItemStack assemble(SimpleContainer inventory) {
        return output.copy();
    }

    public ItemStack getInput() {
        return input;
    }

    public ItemStack getOutput() {
        return output;
    }

    @Override
    public ItemStack getResultItem() {
        return output;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, Ingredient.of(input));
    }

    @Override
    public String toString() {
        return String.format("input=%s, outputs=%s", input, output);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeRegistry.CELL_ANALYZER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeRegistry.CELL_ANALYZER_TYPE.get();
    }
}
