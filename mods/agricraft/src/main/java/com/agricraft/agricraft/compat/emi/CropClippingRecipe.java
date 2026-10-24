package com.agricraft.agricraft.compat.emi;

import com.agricraft.agricraft.compat.AgriRecipeDisplays;

/**
 * A plant's seed and its possible clip products. Ported from AgriCraft 4.0.6, where it is a copy of
 * {@link CropProduceRecipe} with the clip products; here it reuses that class with the clipping category.
 */
public class CropClippingRecipe extends CropProduceRecipe {

	public CropClippingRecipe(AgriRecipeDisplays.PlantDisplay display) {
		super(display, AgriCraftEmiPlugin.CLIPPING_CATEGORY);
	}

}
