package com.agricraft.agricraft.compat.client;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.api.AgriClientApi;
import com.agricraft.agricraft.api.plant.AgriPlant;
import com.agricraft.agricraft.client.gui.AgriGuiGraphics;
import com.agricraft.agricraft.common.util.LangUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Draws a plant as its crop texture, as upstream's JEI plant ingredient renderer and EMI produce recipe do.
 * Shared by the EMI, JEI and REI plugins. Client only.
 */
public final class PlantRendering {

	private PlantRendering() {
	}

	public static void drawPlant(AgriGuiGraphics graphics, AgriPlant plant, int x, int y) {
		Optional<ResourceLocation> optional = AgriApi.getPlantId(plant);
		if (optional.isPresent()) {
			// get the model for the last growth stage and use the particle texture (that is also the crop texture)
			ResourceLocation plantId = optional.get();
			graphics.blit(x, y, 0, 16, 16, AgriClientApi.getPlantModel(plantId.toString(), plant.getInitialGrowthStage().total() - 1).getParticleIcon());
		}
	}

	/** The plant's name and, if it has one, its description. */
	public static List<Component> tooltip(AgriPlant plant) {
		ArrayList<Component> list = new ArrayList<>();
		AgriApi.getPlantId(plant).map(ResourceLocation::toString).ifPresent(id -> {
			list.add(LangUtils.plantName(id));
			Component desc = LangUtils.plantDescription(id);
			if (desc != null) {
				list.add(desc);
			}
		});
		return list;
	}

}
