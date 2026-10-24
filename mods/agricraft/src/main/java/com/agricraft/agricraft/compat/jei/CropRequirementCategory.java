package com.agricraft.agricraft.compat.jei;

import com.agricraft.agricraft.client.gui.AgriGuiGraphics;
import com.agricraft.agricraft.compat.AgriRecipeDisplays;
import com.agricraft.agricraft.compat.client.CropRequirementView;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A plant's growth requirements, browsable by strength and growth stage. Ported from AgriCraft 4.0.6; the
 * drawing, buttons and tooltips (upstream's {@code Recipe} and {@code Btn} classes) live in the shared
 * {@link CropRequirementView}, one per plant, which keeps upstream's per-recipe state.
 */
public class CropRequirementCategory implements IRecipeCategory<AgriRecipeDisplays.PlantDisplay> {

	public static final RecipeType<AgriRecipeDisplays.PlantDisplay> TYPE = AgriCraftJeiPlugin.REQUIREMENT;
	public static final IDrawable BACKGROUND = AgriCraftJeiPlugin.createDrawable(CropRequirementView.BACKGROUND, 0, 0, 128, 128, 128, 128);

	public final IDrawable icon;
	private final Map<ResourceLocation, CropRequirementView> views = new HashMap<>();

	public CropRequirementCategory(IGuiHelper helper) {
		icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, AgriRecipeDisplays.farmland());
	}

	private CropRequirementView view(AgriRecipeDisplays.PlantDisplay recipe) {
		return views.computeIfAbsent(recipe.id(), id -> new CropRequirementView(recipe.plant()));
	}

	@Override
	public RecipeType<AgriRecipeDisplays.PlantDisplay> getRecipeType() {
		return TYPE;
	}

	@Override
	public Component getTitle() {
		return Component.translatable("agricraft.gui.jei.requirement");
	}

	@Override
	public IDrawable getBackground() {
		return BACKGROUND;
	}

	@Override
	public IDrawable getIcon() {
		return icon;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, AgriRecipeDisplays.PlantDisplay recipe, IFocusGroup focuses) {
		// Set shapeless
		builder.setShapeless();
		// Seed
		builder.addSlot(RecipeIngredientRole.INPUT, CropRequirementView.SEED_X, CropRequirementView.SEED_Y)
				.setSlotName("input_seed")
				.addIngredient(VanillaTypes.ITEM_STACK, recipe.seed());
	}

	@Override
	public void draw(AgriRecipeDisplays.PlantDisplay recipe, IRecipeSlotsView recipeSlotsView, PoseStack poseStack, double mouseX, double mouseY) {
		view(recipe).draw(new AgriGuiGraphics(poseStack), mouseX, mouseY);
	}

	@Override
	public boolean handleInput(AgriRecipeDisplays.PlantDisplay recipe, double mouseX, double mouseY, InputConstants.Key input) {
		if (input.getType() == InputConstants.Type.MOUSE && view(recipe).mouseClicked(mouseX, mouseY, input.getValue())) {
			return true;
		}
		return IRecipeCategory.super.handleInput(recipe, mouseX, mouseY, input);
	}

	@Override
	public List<Component> getTooltipStrings(AgriRecipeDisplays.PlantDisplay recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
		List<Component> tooltip = view(recipe).tooltip(mouseX, mouseY);
		return tooltip.isEmpty() ? IRecipeCategory.super.getTooltipStrings(recipe, recipeSlotsView, mouseX, mouseY) : tooltip;
	}

}
