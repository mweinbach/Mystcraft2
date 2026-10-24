package com.agricraft.agricraft.compat.emi;

import com.agricraft.agricraft.client.gui.AgriGuiGraphics;
import com.agricraft.agricraft.compat.AgriRecipeDisplays;
import com.agricraft.agricraft.compat.client.CropRequirementView;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A plant's growth requirements, browsable by strength and growth stage. Ported from AgriCraft 4.0.6; upstream
 * builds it from EMI button, drawable and tooltip widgets, here one widget delegates to the shared
 * {@link CropRequirementView} (same drawing, buttons and tooltips).
 */
public class CropRequirementRecipe implements EmiRecipe {

	private final ResourceLocation id;
	private final List<EmiIngredient> input;
	private final List<EmiStack> output = List.of();
	private final AgriRecipeDisplays.PlantDisplay display;

	public CropRequirementRecipe(AgriRecipeDisplays.PlantDisplay display) {
		this.id = display.id();
		this.display = display;
		input = List.of(AgriCraftEmiPlugin.seed(display.seed()));
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return AgriCraftEmiPlugin.REQUIREMENT_CATEGORY;
	}

	@Override
	public @Nullable ResourceLocation getId() {
		return this.id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return input;
	}

	@Override
	public List<EmiStack> getOutputs() {
		return output;
	}

	@Override
	public int getDisplayWidth() {
		return CropRequirementView.WIDTH;
	}

	@Override
	public int getDisplayHeight() {
		return CropRequirementView.HEIGHT;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		CropRequirementView view = new CropRequirementView(display.plant());
		widgets.addDrawable(0, 0, CropRequirementView.WIDTH, CropRequirementView.HEIGHT, (matrices, mouseX, mouseY, delta) -> view.drawBackground(new AgriGuiGraphics(matrices)));
		widgets.addSlot(input.get(0), CropRequirementView.SEED_X - 1, CropRequirementView.SEED_Y - 1).drawBack(false);
		widgets.add(new RequirementWidget(view));
	}

	/** The interactive part of the page, drawn over the background and seed slot. */
	private static final class RequirementWidget extends Widget {

		private final CropRequirementView view;
		private final Bounds bounds = new Bounds(0, 0, CropRequirementView.WIDTH, CropRequirementView.HEIGHT);

		private RequirementWidget(CropRequirementView view) {
			this.view = view;
		}

		@Override
		public Bounds getBounds() {
			return bounds;
		}

		@Override
		public void render(PoseStack matrices, int mouseX, int mouseY, float delta) {
			view.draw(new AgriGuiGraphics(matrices), mouseX, mouseY);
		}

		@Override
		public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
			return view.tooltip(mouseX, mouseY).stream().map(Component::getVisualOrderText).map(ClientTooltipComponent::create).toList();
		}

		@Override
		public boolean mouseClicked(int mouseX, int mouseY, int button) {
			return view.mouseClicked(mouseX, mouseY, button);
		}

	}

}
