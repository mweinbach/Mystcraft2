package com.agricraft.agricraft.compat.jei;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.api.plant.AgriPlant;
import com.agricraft.agricraft.client.gui.AgriGuiGraphics;
import com.agricraft.agricraft.common.util.LangUtils;
import com.agricraft.agricraft.compat.client.PlantRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Plants as a JEI ingredient type, drawn as their crop texture. Ported from AgriCraft 4.0.6. */
public class PlantIngredient {

	public static final IIngredientType<AgriPlant> TYPE = () -> AgriPlant.class;

	public static final IIngredientHelper<AgriPlant> HELPER = new IIngredientHelper<>() {
		@Override
		public IIngredientType<AgriPlant> getIngredientType() {
			return TYPE;
		}

		@Override
		public String getDisplayName(AgriPlant plant) {
			return LangUtils.plantName(id(plant)).getString();
		}

		@Override
		public String getUniqueId(AgriPlant plant, UidContext context) {
			return id(plant);
		}

		@Override
		public ResourceLocation getResourceLocation(AgriPlant plant) {
			return AgriApi.getPlantId(plant).orElse(new ResourceLocation("agricraft:unknown"));
		}

		@Override
		public AgriPlant copyIngredient(AgriPlant plant) {
			return plant;
		}

		@Override
		public String getErrorInfo(AgriPlant plant) {
			return plant == null ? "null" : id(plant);
		}
	};

	public static final IIngredientRenderer<AgriPlant> RENDERER = new IIngredientRenderer<>() {
		@Override
		public void render(PoseStack poseStack, AgriPlant plant) {
			PlantRendering.drawPlant(new AgriGuiGraphics(poseStack), plant, 0, 0);
		}

		@Override
		public List<Component> getTooltip(AgriPlant ingredient, TooltipFlag tooltipFlag) {
			return PlantRendering.tooltip(ingredient);
		}
	};

	private static String id(AgriPlant plant) {
		return AgriApi.getPlantId(plant).map(ResourceLocation::toString).orElse("agricraft:unknown");
	}

}
