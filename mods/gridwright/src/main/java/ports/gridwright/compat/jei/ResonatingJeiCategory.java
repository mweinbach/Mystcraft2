package ports.gridwright.compat.jei;

import ports.gridwright.compat.ResonatingDisplays;
import ports.gridwright.recipe.ResonatingRecipe;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** One input slot, one output slot and the recipe's GP and time. Client only. */
final class ResonatingJeiCategory implements IRecipeCategory<ResonatingRecipe> {

    private static final int TEXT = 0xFF404040;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slot;

    ResonatingJeiCategory(IGuiHelper gui) {
        this.background = gui.createBlankDrawable(ResonatingDisplays.WIDTH, ResonatingDisplays.HEIGHT);
        this.icon = gui.createDrawableItemStack(ResonatingDisplays.workstation());
        this.slot = gui.getSlotDrawable();
    }

    @Override
    public RecipeType<ResonatingRecipe> getRecipeType() {
        return GridwrightJeiPlugin.RESONATING;
    }

    @Override
    public Component getTitle() {
        return ResonatingDisplays.title();
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ResonatingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, ResonatingDisplays.INPUT_X + 1, ResonatingDisplays.SLOT_Y + 1)
                .addItemStacks(ResonatingDisplays.inputs(recipe)).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.OUTPUT, ResonatingDisplays.OUTPUT_X + 1, ResonatingDisplays.SLOT_Y + 1)
                .addItemStack(ResonatingDisplays.output(recipe)).setBackground(slot, -1, -1);
    }

    @Override
    public void draw(ResonatingRecipe recipe, IRecipeSlotsView slots, PoseStack poseStack, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        font.draw(poseStack, "->", ResonatingDisplays.ARROW_X + 8, ResonatingDisplays.SLOT_Y + 5, TEXT);
        font.draw(poseStack, ResonatingDisplays.details(recipe), ResonatingDisplays.INPUT_X, ResonatingDisplays.TEXT_Y, TEXT);
    }
}
