package com.technovision.advancedgenetics.common.recipe.cellanalyzer;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

public class CellAnalyzerRecipeSerializer implements RecipeSerializer<CellAnalyzerRecipe> {

    @Override
    public CellAnalyzerRecipe fromJson(ResourceLocation id, JsonObject json) {
        ItemStack input = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "input"));
        ItemStack output = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
        return new CellAnalyzerRecipe(id, input, output);
    }

    @Override
    public CellAnalyzerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        ItemStack input = buf.readItem();
        ItemStack output = buf.readItem();
        return new CellAnalyzerRecipe(id, input, output);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, CellAnalyzerRecipe recipe) {
        buf.writeItem(recipe.getInput());
        buf.writeItem(recipe.getOutput());
    }
}
