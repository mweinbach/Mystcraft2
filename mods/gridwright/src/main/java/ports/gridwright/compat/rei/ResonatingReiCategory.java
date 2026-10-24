package ports.gridwright.compat.rei;

import ports.gridwright.compat.ResonatingDisplays;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** REI's resonating category: input, arrow, output, GP and time. Client only. */
final class ResonatingReiCategory implements DisplayCategory<ResonatingReiDisplay> {

    private static final int PADDING = 5;

    @Override
    public CategoryIdentifier<? extends ResonatingReiDisplay> getCategoryIdentifier() {
        return ResonatingReiDisplay.CATEGORY;
    }

    @Override
    public Component getTitle() {
        return ResonatingDisplays.title();
    }

    @Override
    public Renderer getIcon() {
        return EntryIngredients.ofItemStacks(List.of(ResonatingDisplays.workstation())).get(0);
    }

    @Override
    public List<Widget> setupDisplay(ResonatingReiDisplay display, Rectangle bounds) {
        int x = bounds.x + PADDING;
        int y = bounds.y + PADDING;
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        widgets.add(Widgets.createSlot(new Point(x + ResonatingDisplays.INPUT_X + 1, y + ResonatingDisplays.SLOT_Y + 1))
                .entries(display.getInputEntries().get(0)).markInput());
        widgets.add(Widgets.createArrow(new Point(x + ResonatingDisplays.ARROW_X, y + ResonatingDisplays.SLOT_Y)));
        widgets.add(Widgets.createSlot(new Point(x + ResonatingDisplays.OUTPUT_X + 1, y + ResonatingDisplays.SLOT_Y + 1))
                .entries(display.getOutputEntries().get(0)).markOutput());
        widgets.add(Widgets.createLabel(new Point(x + ResonatingDisplays.INPUT_X, y + ResonatingDisplays.TEXT_Y),
                ResonatingDisplays.details(display.recipe())).leftAligned().noShadow().color(0xFF404040, 0xFFBBBBBB));
        return widgets;
    }

    @Override
    public int getDisplayHeight() {
        return ResonatingDisplays.HEIGHT + 2 * PADDING;
    }

    @Override
    public int getDisplayWidth(ResonatingReiDisplay display) {
        return ResonatingDisplays.WIDTH + 2 * PADDING;
    }
}
