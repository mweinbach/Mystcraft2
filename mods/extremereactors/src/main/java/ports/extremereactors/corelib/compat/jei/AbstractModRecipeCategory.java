/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.compat.jei;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A JEI recipe category with a fixed type, title, background and an item as its icon. Subclasses lay out the
 * recipe slots and may draw extras and tooltips on top of the background.
 */
public abstract class AbstractModRecipeCategory<T>
        implements IRecipeCategory<T> {

    protected AbstractModRecipeCategory(final RecipeType<T> type, final Component title, final ItemStack icon,
                                        final IGuiHelper guiHelper, final IDrawable background) {

        this._type = type;
        this._title = title;
        this._icon = guiHelper.createDrawableItemStack(icon);
        this._background = background;
    }

    //region IRecipeCategory

    @Override
    public RecipeType<T> getRecipeType() {
        return this._type;
    }

    @Override
    public Component getTitle() {
        return this._title;
    }

    @Override
    public IDrawable getBackground() {
        return this._background;
    }

    @Override
    public IDrawable getIcon() {
        return this._icon;
    }

    //endregion
    //region internals

    private final RecipeType<T> _type;
    private final Component _title;
    private final IDrawable _icon;
    private final IDrawable _background;

    //endregion
}
