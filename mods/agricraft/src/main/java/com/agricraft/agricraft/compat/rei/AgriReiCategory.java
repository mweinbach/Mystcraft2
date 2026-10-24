package com.agricraft.agricraft.compat.rei;

import com.agricraft.agricraft.api.plant.AgriPlant;
import com.agricraft.agricraft.client.gui.AgriGuiGraphics;
import com.agricraft.agricraft.compat.AgriRecipeDisplays;
import com.agricraft.agricraft.compat.client.CropRequirementView;
import com.agricraft.agricraft.compat.client.PlantRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Tooltip;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.WidgetWithBounds;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * REI's AgriCraft categories, laid out like the JEI ones on the same 128 x 128 textures. Original port code
 * (upstream AgriCraft 4 has no REI plugin). Client only.
 */
abstract class AgriReiCategory implements DisplayCategory<AgriReiDisplay> {

	private static final int PADDING = 4;
	private static final int SIZE = 128;

	private final CategoryIdentifier<AgriReiDisplay> identifier;
	private final String title;
	private final ItemStack icon;

	AgriReiCategory(CategoryIdentifier<AgriReiDisplay> identifier, String title, ItemStack icon) {
		this.identifier = identifier;
		this.title = title;
		this.icon = icon;
	}

	@Override
	public CategoryIdentifier<? extends AgriReiDisplay> getCategoryIdentifier() {
		return identifier;
	}

	@Override
	public Component getTitle() {
		return Component.translatable(title);
	}

	@Override
	public Renderer getIcon() {
		return EntryIngredients.ofItemStacks(List.of(icon)).get(0);
	}

	@Override
	public int getDisplayHeight() {
		return SIZE + 2 * PADDING;
	}

	@Override
	public int getDisplayWidth(AgriReiDisplay display) {
		return SIZE + 2 * PADDING;
	}

	@Override
	public List<Widget> setupDisplay(AgriReiDisplay display, Rectangle bounds) {
		Point origin = new Point(bounds.x + PADDING, bounds.y + PADDING);
		List<Widget> widgets = new ArrayList<>();
		widgets.add(Widgets.createRecipeBase(bounds));
		addWidgets(display, origin, widgets);
		return widgets;
	}

	/** Adds the category's widgets; positions are relative to {@code origin}, the page's top left corner. */
	abstract void addWidgets(AgriReiDisplay display, Point origin, List<Widget> widgets);

	static Widget texture(ResourceLocation texture, Point origin) {
		return Widgets.createTexturedWidget(texture, origin.x, origin.y, 0, 0, SIZE, SIZE, SIZE, SIZE);
	}

	static void slot(List<Widget> widgets, Point origin, int x, int y, EntryIngredient entries, boolean input) {
		var slot = Widgets.createSlot(new Point(origin.x + x, origin.y + y)).entries(entries).disableBackground();
		widgets.add(input ? slot.markInput() : slot.markOutput());
	}

	/** A plant's crop texture with its name and description as tooltip (JEI's plant ingredient). */
	static void plant(List<Widget> widgets, Point origin, int x, int y, AgriPlant plant) {
		int px = origin.x + x;
		int py = origin.y + y;
		widgets.add(Widgets.createDrawableWidget((helper, matrices, mouseX, mouseY, delta) -> PlantRendering.drawPlant(new AgriGuiGraphics(matrices), plant, px, py)));
		widgets.add(Widgets.createTooltip(new Rectangle(px, py, 16, 16), PlantRendering.tooltip(plant)));
	}

	/** Two parent seeds (and plants) give a child seed (and plant); the JEI mutation layout. */
	static final class Mutation extends AgriReiCategory {

		Mutation() {
			super(AgriReiDisplay.MUTATION, "agricraft.gui.jei.mutation", AgriRecipeDisplays.cropSticks().get(0));
		}

		@Override
		void addWidgets(AgriReiDisplay display, Point origin, List<Widget> widgets) {
			widgets.add(texture(AgriRecipeDisplays.MUTATION_TEXTURE, origin));
			slot(widgets, origin, 16, 6, display.getInputEntries().get(0), true);
			slot(widgets, origin, 96, 6, display.getInputEntries().get(1), true);
			slot(widgets, origin, 56, 2, display.getOutputEntries().get(0), false);
			List<AgriPlant> plants = display.plants();
			plant(widgets, origin, 25, 40, plants.get(0));
			plant(widgets, origin, 87, 40, plants.get(1));
			plant(widgets, origin, 56, 40, plants.get(2));
		}

	}

	/** A seed (and plant) and its products or clip products; the JEI produce layout. */
	static final class Products extends AgriReiCategory {

		Products(CategoryIdentifier<AgriReiDisplay> identifier, String title, ItemStack icon) {
			super(identifier, title, icon);
		}

		@Override
		void addWidgets(AgriReiDisplay display, Point origin, List<Widget> widgets) {
			widgets.add(texture(AgriRecipeDisplays.PRODUCE_TEXTURE, origin));
			slot(widgets, origin, 16, 9, display.getInputEntries().get(0), true);
			plant(widgets, origin, 16, 49, display.plants().get(0));
			List<EntryIngredient> outputs = display.getOutputEntries();
			int index = 0;
			for (int y = 33; y < 83 && index < outputs.size(); y += 18) {
				for (int x = 75; x < 129 && index < outputs.size(); x += 18) {
					slot(widgets, origin, x, y, outputs.get(index++), false);
				}
			}
		}

	}

	/** The interactive growth requirement page shared with EMI and JEI. */
	static final class Requirement extends AgriReiCategory {

		Requirement() {
			super(AgriReiDisplay.REQUIREMENT, "agricraft.gui.jei.requirement", AgriRecipeDisplays.farmland());
		}

		@Override
		void addWidgets(AgriReiDisplay display, Point origin, List<Widget> widgets) {
			widgets.add(texture(CropRequirementView.BACKGROUND, origin));
			slot(widgets, origin, CropRequirementView.SEED_X, CropRequirementView.SEED_Y, display.getInputEntries().get(0), true);
			widgets.add(new RequirementWidget(new CropRequirementView(display.plants().get(0)), new Rectangle(origin.x, origin.y, SIZE, SIZE)));
		}

	}

	/** Draws {@link CropRequirementView} in page coordinates, forwards clicks and queues its tooltips. */
	private static final class RequirementWidget extends WidgetWithBounds {

		private final CropRequirementView view;
		private final Rectangle bounds;

		private RequirementWidget(CropRequirementView view, Rectangle bounds) {
			this.view = view;
			this.bounds = bounds;
		}

		@Override
		public Rectangle getBounds() {
			return bounds;
		}

		@Override
		public void render(PoseStack poseStack, int mouseX, int mouseY, float delta) {
			poseStack.pushPose();
			poseStack.translate(bounds.x, bounds.y, 0);
			view.draw(new AgriGuiGraphics(poseStack), mouseX - bounds.x, mouseY - bounds.y);
			poseStack.popPose();
			if (containsMouse(mouseX, mouseY)) {
				List<Component> tooltip = view.tooltip(mouseX - bounds.x, mouseY - bounds.y);
				if (!tooltip.isEmpty()) {
					Tooltip.create(new Point(mouseX, mouseY), tooltip).queue();
				}
			}
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			return containsMouse(mouseX, mouseY) && view.mouseClicked(mouseX - bounds.x, mouseY - bounds.y, button);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

	}

}
