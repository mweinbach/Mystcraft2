package com.agricraft.agricraft.compat.jei;

/**
 * A plant's seed and its possible clip products. Ported from AgriCraft 4.0.6, where it is a copy of
 * {@link CropProduceCategory} with the clip products; here it reuses that class with its own type, title and icon.
 */
public class CropClippingCategory extends CropProduceCategory {

	public CropClippingCategory() {
		super(AgriCraftJeiPlugin.CLIPPING, "agricraft.gui.jei.clipping", "textures/item/clipper.png");
	}

}
