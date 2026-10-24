package com.agricraft.agricraft.compat.jei;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.compat.AgriRecipeDisplays;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Two parent plants give a child plant. Ported from AgriCraft 4.0.6. */
public class CropMutationCategory implements IRecipeCategory<AgriRecipeDisplays.Mutation> {

	public static final RecipeType<AgriRecipeDisplays.Mutation> TYPE = AgriCraftJeiPlugin.MUTATION;

	public static final IDrawable ICON = AgriCraftJeiPlugin.createDrawable(new ResourceLocation(AgriApi.MOD_ID, "textures/item/wooden_crop_sticks.png"), 0, 0, 16, 16, 16, 16);
	public static final IDrawable BACKGROUND = AgriCraftJeiPlugin.createDrawable(AgriRecipeDisplays.MUTATION_TEXTURE, 0, 0, 128, 128, 128, 128);

	@Override
	public RecipeType<AgriRecipeDisplays.Mutation> getRecipeType() {
		return TYPE;
	}

	@Override
	public Component getTitle() {
		return Component.translatable("agricraft.gui.jei.mutation");
	}

	@Override
	public IDrawable getBackground() {
		return BACKGROUND;
	}

	@Override
	public IDrawable getIcon() {
		return ICON;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, AgriRecipeDisplays.Mutation mutation, IFocusGroup focuses) {
		// Set shapeless
		builder.setShapeless();

		// First input
		builder.addSlot(RecipeIngredientRole.INPUT, 16, 6)
				.setSlotName("input_seed_1")
				.addIngredient(VanillaTypes.ITEM_STACK, mutation.parent1Seed());
		builder.addSlot(RecipeIngredientRole.INPUT, 25, 40)
				.setSlotName("input_plant_1")
				.setCustomRenderer(PlantIngredient.TYPE, PlantIngredient.RENDERER)
				.addIngredient(PlantIngredient.TYPE, mutation.parent1());

		// Second input
		builder.addSlot(RecipeIngredientRole.INPUT, 96, 6)
				.setSlotName("input_seed_2")
				.addIngredient(VanillaTypes.ITEM_STACK, mutation.parent2Seed());
		builder.addSlot(RecipeIngredientRole.INPUT, 87, 40)
				.setSlotName("input_plant_2")
				.setCustomRenderer(PlantIngredient.TYPE, PlantIngredient.RENDERER)
				.addIngredient(PlantIngredient.TYPE, mutation.parent2());

		// Output
		builder.addSlot(RecipeIngredientRole.OUTPUT, 56, 2)
				.setSlotName("output_seed")
				.addIngredient(VanillaTypes.ITEM_STACK, mutation.childSeed());
		builder.addSlot(RecipeIngredientRole.INPUT, 56, 40)
				.setSlotName("output_plant")
				.setCustomRenderer(PlantIngredient.TYPE, PlantIngredient.RENDERER)
				.addIngredient(PlantIngredient.TYPE, mutation.child());
	}

}
