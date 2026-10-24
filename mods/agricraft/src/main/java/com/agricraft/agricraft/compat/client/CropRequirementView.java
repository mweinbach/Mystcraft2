package com.agricraft.agricraft.compat.client;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.api.AgriClientApi;
import com.agricraft.agricraft.api.codecs.AgriSoilCondition;
import com.agricraft.agricraft.api.crop.AgriGrowthStage;
import com.agricraft.agricraft.api.plant.AgriPlant;
import com.agricraft.agricraft.api.requirement.AgriGrowthConditionRegistry;
import com.agricraft.agricraft.api.requirement.AgriSeason;
import com.agricraft.agricraft.client.gui.AgriGuiGraphics;
import com.agricraft.agricraft.common.util.LangUtils;
import com.agricraft.agricraft.common.util.Platform;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Locale;

/**
 * The interactive growth requirement page of one plant: strength and growth stage buttons, the fertile light
 * levels, soil properties and seasons, and the plant rendered on a cycling suitable soil. The drawing and
 * tooltip code is upstream's {@code CropRequirementCategory} (JEI) / {@code CropRequirementRecipe} (EMI),
 * moved into one class the EMI, JEI and REI plugins share; coordinates are relative to the 128 x 128 page.
 * Client only.
 */
public class CropRequirementView {

	public static final int WIDTH = 128;
	public static final int HEIGHT = 128;
	public static final ResourceLocation BACKGROUND = new ResourceLocation(AgriApi.MOD_ID, "textures/gui/jei/crop_requirement.png");
	public static final ResourceLocation COMPONENTS = new ResourceLocation(AgriApi.MOD_ID, "textures/gui/jei/crop_requirement_components.png");
	public static final ResourceLocation GUI_COMPONENTS = new ResourceLocation(AgriApi.MOD_ID, "textures/gui/gui_components.png");
	public static final int[] HUMIDITY_OFFSETS = {8, 8, 10, 10, 10, 7};
	public static final int[] ACIDITY_OFFSETS = {7, 8, 7, 8, 8, 8, 6};
	public static final int[] NUTRIENTS_OFFSETS = {6, 8, 9, 9, 11, 10};
	/** Where the seed slot goes (upstream JEI position). */
	public static final int SEED_X = 56;
	public static final int SEED_Y = 3;

	private final AgriPlant plant;
	private final String plantId;
	private final Btn incStrButton;
	private final Btn decStrButton;
	private final Btn incStageButton;
	private final Btn decStageButton;
	private int currentStrength = AgriApi.getStatRegistry().strengthStat().getMin();
	private AgriGrowthStage currentStage;
	private List<Block> soils;
	private int soil;
	private long lastTime;

	public CropRequirementView(AgriPlant plant) {
		this.plant = plant;
		this.plantId = AgriApi.getPlantId(plant).map(ResourceLocation::toString).orElse("");
		this.currentStage = plant.getInitialGrowthStage();
		this.incStrButton = new Btn(104, 10, this::incrementStrength, true);
		this.decStrButton = new Btn(104, 71, this::decrementStrength, false);
		this.incStageButton = new Btn(92, 10, this::incrementStage, true);
		this.decStageButton = new Btn(92, 71, this::decrementStage, false);
		this.updateSoils();
		this.lastTime = System.currentTimeMillis();
	}

	public void drawBackground(AgriGuiGraphics guiGraphics) {
		guiGraphics.blit(BACKGROUND, 0, 0, 0, 0, WIDTH, HEIGHT, 128, 128);
	}

	/** Everything but the background and the seed slot. */
	public void draw(AgriGuiGraphics guiGraphics, double mouseX, double mouseY) {
		// render buttons
		incStrButton.render(guiGraphics, mouseX, mouseY);
		decStrButton.render(guiGraphics, mouseX, mouseY);
		incStageButton.render(guiGraphics, mouseX, mouseY);
		decStageButton.render(guiGraphics, mouseX, mouseY);
		// render strength increment
		for (int i = 0; i < currentStrength; ++i) {
			guiGraphics.blit(COMPONENTS, 105, 66 - i * 5, 0, 66, 7, 3, 128, 128);
		}
		// render stage increment
		int maxHeight = 48;
		int stageHeight = (int) (maxHeight * currentStage.growthPercentage());
		int stageY = 21 + maxHeight - stageHeight;
		guiGraphics.blit(COMPONENTS, 93, stageY, 7, stageHeight, 0, 69, 7, 1, 128, 128);
		// render light levels
		for (int i = 15; i >= 0; --i) {
			boolean fertile = AgriGrowthConditionRegistry.getLight().apply(plant, currentStrength, i).isFertile();
			if (fertile) {
				guiGraphics.blit(COMPONENTS, 32, 26 + 3 * (15 - i), 3, 3, 0, 18 + 3 * (15 - i), 3, 3, 128, 128);
			}
		}
		// render soil property icons
		for (AgriSoilCondition.Humidity humidity : AgriSoilCondition.Humidity.values()) {
			if (humidity.isValid() && AgriGrowthConditionRegistry.getHumidity().apply(plant, currentStrength, humidity).isFertile()) {
				int index = humidity.ordinal();
				int offset = offset(HUMIDITY_OFFSETS, index);
				guiGraphics.blit(GUI_COMPONENTS, 37 + offset, 83, HUMIDITY_OFFSETS[index], 12, offset, 0, HUMIDITY_OFFSETS[index], 12, 128, 128);
			}
		}
		for (AgriSoilCondition.Acidity acidity : AgriSoilCondition.Acidity.values()) {
			if (acidity.isValid() && AgriGrowthConditionRegistry.getAcidity().apply(plant, currentStrength, acidity).isFertile()) {
				int index = acidity.ordinal();
				int offset = offset(ACIDITY_OFFSETS, index);
				guiGraphics.blit(GUI_COMPONENTS, 37 + offset, 96, ACIDITY_OFFSETS[index], 12, offset, 12, ACIDITY_OFFSETS[index], 12, 128, 128);
			}
		}
		for (AgriSoilCondition.Nutrients nutrients : AgriSoilCondition.Nutrients.values()) {
			if (nutrients.isValid() && AgriGrowthConditionRegistry.getNutrients().apply(plant, currentStrength, nutrients).isFertile()) {
				int index = nutrients.ordinal();
				int offset = offset(NUTRIENTS_OFFSETS, index);
				guiGraphics.blit(GUI_COMPONENTS, 37 + offset, 109, NUTRIENTS_OFFSETS[index], 12, offset, 24, NUTRIENTS_OFFSETS[index], 12, 128, 128);
			}
		}
		// render seasons
		if (AgriApi.getSeasonLogic().isActive()) {
			for (AgriSeason season : AgriSeason.values()) {
				if (season != AgriSeason.ANY && AgriGrowthConditionRegistry.getSeason().apply(plant, currentStrength, season).isFertile()) {
					guiGraphics.blit(GUI_COMPONENTS, 17, 24 + 13 * season.ordinal(), 10 * season.ordinal(), 44, 10, 12, 128, 128);
				}
			}
		}
		// render soils and plant stage
		long l = System.currentTimeMillis();
		if (lastTime + 1500 <= l && !Screen.hasShiftDown()) {  // change soil to render
			this.soil++;
			if (this.soil >= this.soils.size()) {
				this.soil = 0;
			}
			lastTime = l;
		}
		PoseStack stack = guiGraphics.pose();
		stack.pushPose();
		Lighting.setupForFlatItems();
		stack.translate(56, 53, 0);
		stack.translate(-4, 12, 0);
		stack.scale(16, -16, 1);
		stack.mulPose(Vector3f.XP.rotationDegrees(45));
		stack.mulPose(Vector3f.YP.rotationDegrees(45));
		MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
		// render soil
		if (!soils.isEmpty() && soil < soils.size()) {
			stack.pushPose();
			Minecraft.getInstance().getBlockRenderer().renderSingleBlock(soils.get(soil).defaultBlockState(), stack, bufferSource, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			stack.popPose();
		}
		// render plant
		BakedModel model = AgriClientApi.getPlantModel(plantId, currentStage.index());
		stack.pushPose();
		stack.translate(0, 1, 0);
		Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(stack.last(), bufferSource.getBuffer(RenderType.cutoutMipped()), null, model, 1, 1, 1, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		stack.popPose();
		bufferSource.endBatch();
		stack.popPose();
	}

	/** Presses the button under the mouse, if any (left button only). */
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return false;
		}
		for (Btn btn : new Btn[]{incStrButton, decStrButton, incStageButton, decStageButton}) {
			if (btn.isMouseOver(mouseX, mouseY)) {
				btn.onPress.run();
				return true;
			}
		}
		return false;
	}

	/** The tooltip at the mouse, or an empty list. */
	public List<Component> tooltip(double mouseX, double mouseY) {
		if (104 <= mouseX && mouseX <= 113 && 20 <= mouseY && mouseY <= 70) {
			return List.of(Component.translatable("agricraft.tooltip.jei.strength", currentStrength));
		}
		if (92 <= mouseX && mouseX <= 101 && 20 <= mouseY && mouseY <= 70) {
			return List.of(Component.translatable("agricraft.tooltip.jei.stage", currentStage.index()));
		}
		if (32 <= mouseX && mouseX <= 35 && 26 <= mouseY && mouseY <= 73) {
			int light = 15 - ((int) (mouseY - 26) / 3);
			return List.of(Component.translatable("agricraft.tooltip.jei.light", light));
		}
		if (83 <= mouseY && mouseY <= 95) {
			int i = column(HUMIDITY_OFFSETS, mouseX);
			if (i >= 0) {
				return List.of(Component.translatable("agricraft.soil.humidity." + AgriSoilCondition.Humidity.values()[i].name().toLowerCase(Locale.ROOT)));
			}
		}
		if (96 <= mouseY && mouseY <= 108) {
			int i = column(ACIDITY_OFFSETS, mouseX);
			if (i >= 0) {
				return List.of(Component.translatable("agricraft.soil.acidity." + AgriSoilCondition.Acidity.values()[i].name().toLowerCase(Locale.ROOT)));
			}
		}
		if (109 <= mouseY && mouseY <= 121) {
			int i = column(NUTRIENTS_OFFSETS, mouseX);
			if (i >= 0) {
				return List.of(Component.translatable("agricraft.soil.nutrients." + AgriSoilCondition.Nutrients.values()[i].name().toLowerCase(Locale.ROOT)));
			}
		}
		if (50 <= mouseX && mouseX <= 76 && 34 <= mouseY && mouseY <= 58) {
			Component desc = LangUtils.plantDescription(plantId);
			return desc == null ? List.of(LangUtils.plantName(plantId)) : List.of(LangUtils.plantName(plantId), desc);
		}
		if (50 <= mouseX && mouseX <= 76 && 58 <= mouseY && mouseY <= 74 && !soils.isEmpty() && soil < soils.size()) {
			Minecraft minecraft = Minecraft.getInstance();
			return new ItemStack(soils.get(soil)).getTooltipLines(minecraft.player, minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
		}
		if (AgriApi.getSeasonLogic().isActive() && 17 <= mouseX && mouseX <= 29) {
			if (24 <= mouseY && mouseY <= 36) {
				return List.of(LangUtils.seasonName(AgriSeason.SPRING));
			}
			if (37 <= mouseY && mouseY <= 49) {
				return List.of(LangUtils.seasonName(AgriSeason.SUMMER));
			}
			if (50 <= mouseY && mouseY <= 62) {
				return List.of(LangUtils.seasonName(AgriSeason.AUTUMN));
			}
			if (63 <= mouseY && mouseY <= 74) {
				return List.of(LangUtils.seasonName(AgriSeason.WINTER));
			}
		}
		return List.of();
	}

	public void incrementStrength() {
		this.currentStrength = Math.min(AgriApi.getStatRegistry().strengthStat().getMax(), currentStrength + 1);
		this.updateSoils();
	}

	public void decrementStrength() {
		this.currentStrength = Math.max(AgriApi.getStatRegistry().strengthStat().getMin(), currentStrength - 1);
		this.updateSoils();
	}

	public void incrementStage() {
		this.currentStage = this.currentStage.getNext(null, null);
	}

	public void decrementStage() {
		this.currentStage = this.currentStage.getPrevious(null, null);
	}

	private void updateSoils() {
		this.soils = AgriApi.getSoilRegistry().map(registry -> registry.stream().filter(soil -> {
							boolean humidity = AgriGrowthConditionRegistry.getHumidity().apply(this.plant, this.currentStrength, soil.humidity()).isFertile();
							boolean acidity = AgriGrowthConditionRegistry.getAcidity().apply(this.plant, this.currentStrength, soil.acidity()).isFertile();
							boolean nutrients = AgriGrowthConditionRegistry.getNutrients().apply(this.plant, this.currentStrength, soil.nutrients()).isFertile();
							return humidity && acidity && nutrients;
						})
						.flatMap(soil -> soil.variants().stream())
						.flatMap(variant -> Platform.get().getBlocksFromLocation(variant.block()).stream())
						.distinct()
						.toList())
				.orElse(List.of());
		this.soil = 0;
	}

	private static int offset(int[] offsets, int index) {
		int offset = 0;
		for (int i = 0; i < index; ++i) {
			offset += offsets[i];
		}
		return offset;
	}

	/** The soil property column under {@code mouseX}, or -1. */
	private static int column(int[] offsets, double mouseX) {
		int offset = 0;
		for (int i = 0; i < offsets.length; i++) {
			if (37 + offset <= mouseX && mouseX <= 37 + offset + offsets[i]) {
				return i;
			}
			offset += offsets[i];
		}
		return -1;
	}

	/** A 9 x 9 increment/decrement button (upstream's {@code Btn}). */
	private static final class Btn {

		private final int x;
		private final int y;
		private final Runnable onPress;
		private final boolean isIncrement;

		private Btn(int x, int y, Runnable onPress, boolean isIncrement) {
			this.x = x;
			this.y = y;
			this.onPress = onPress;
			this.isIncrement = isIncrement;
		}

		private boolean isMouseOver(double mouseX, double mouseY) {
			return this.x <= mouseX && mouseX < this.x + 9 && this.y <= mouseY && mouseY < this.y + 9;
		}

		private void render(AgriGuiGraphics guiGraphics, double mouseX, double mouseY) {
			int vOffset = isIncrement ? 9 : 0;
			int uOffset = 0;
			if (this.isMouseOver(mouseX, mouseY)) {
				uOffset = Minecraft.getInstance().mouseHandler.isLeftPressed() ? 9 : 18;
			}
			guiGraphics.blit(COMPONENTS, this.x, this.y, uOffset, vOffset, 9, 9, 128, 128);
		}

	}

}
