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
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** A plant's seed and its possible products. Ported from AgriCraft 4.0.6. */
public class CropProduceCategory implements IRecipeCategory<AgriRecipeDisplays.PlantDisplay> {

	public static final IDrawable BACKGROUND = AgriCraftJeiPlugin.createDrawable(AgriRecipeDisplays.PRODUCE_TEXTURE, 0, 0, 128, 128, 128, 128);

	private final RecipeType<AgriRecipeDisplays.PlantDisplay> type;
	private final String title;
	private final IDrawable icon;

	public CropProduceCategory() {
		this(AgriCraftJeiPlugin.PRODUCE, "agricraft.gui.jei.produce", "textures/item/debugger.png");
	}

	protected CropProduceCategory(RecipeType<AgriRecipeDisplays.PlantDisplay> type, String title, String icon) {
		this.type = type;
		this.title = title;
		this.icon = AgriCraftJeiPlugin.createDrawable(new ResourceLocation(AgriApi.MOD_ID, icon), 0, 0, 16, 16, 16, 16);
	}

	@Override
	public RecipeType<AgriRecipeDisplays.PlantDisplay> getRecipeType() {
		return type;
	}

	@Override
	public Component getTitle() {
		return Component.translatable(title);
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
	public void setRecipe(IRecipeLayoutBuilder builder, AgriRecipeDisplays.PlantDisplay display, IFocusGroup focuses) {
		// Set shapeless
		builder.setShapeless();

		// inputs
		builder.addSlot(RecipeIngredientRole.INPUT, 16, 9)
				.setSlotName("input_seed")
				.addIngredient(VanillaTypes.ITEM_STACK, display.seed());
		builder.addSlot(RecipeIngredientRole.INPUT, 16, 49)
				.setSlotName("input_plant")
				.setCustomRenderer(PlantIngredient.TYPE, PlantIngredient.RENDERER)
				.addIngredient(PlantIngredient.TYPE, display.plant());

		// outputs
		int index = 0;
		List<ItemStack> products = display.outputs();
		for (int y = 33; y < 83; y += 18) {
			if (index >= products.size()) {
				break;
			}
			for (int x = 75; x < 129; x += 18) {
				if (index < products.size()) {
					builder.addSlot(RecipeIngredientRole.OUTPUT, x, y).setSlotName("output_" + index).addItemStack(products.get(index));
					index++;
				} else {
					break;
				}
			}
		}
	}

}
