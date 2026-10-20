/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemStack;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.data.geometry.Rectangle;
import ports.extremereactors.corelib.data.gfx.Colour;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

/**
 * A fixed size box that shows a solid colour, an item stack, or both. Separator lines are thin coloured statics.
 */
public class Static
        extends AbstractControl {

    public Static(final ModContainerScreen<? extends ModContainer> gui, final int width, final int height) {
        this(gui, gui.nextGenericName(), width, height);
    }

    public Static(final ModContainerScreen<? extends ModContainer> gui, final String name, final int width,
                  final int height) {

        super(gui, name);
        this.setDesiredDimension(width, height);
        this._stack = ItemStack.EMPTY;
        this._stackMode = StackMode.Plain;
    }

    public Static setColor(final Colour colour) {

        this.setBackground(colour);
        return this;
    }

    /** Show the stack without its count. */
    public void setStack(final ItemStack stack) {
        this.setStack(stack, StackMode.Plain);
    }

    /** Show the stack with its count. */
    public void setStackWithCount(final ItemStack stack) {
        this.setStack(stack, StackMode.WithCount);
    }

    /** Show the stack as an inventory slot does: with its count and durability bar. */
    public void setStackAsInventory(final ItemStack stack) {
        this.setStack(stack, StackMode.Inventory);
    }

    //region AbstractControl

    @Override
    public void onPaint(final PoseStack matrix, final float partialTicks, final int mouseX, final int mouseY) {

        if (this._stack.isEmpty()) {
            return;
        }

        final Rectangle area = this.getScreenPaddingRect();
        final ItemRenderer renderer = this.getGui().getItemRenderer();

        renderer.renderAndDecorateItem(this._stack, area.getX1(), area.getY1());

        if (StackMode.Plain != this._stackMode) {
            renderer.renderGuiItemDecorations(this.getFont(), this._stack, area.getX1(), area.getY1());
        }
    }

    //endregion
    //region internals

    private enum StackMode {
        Plain,
        WithCount,
        Inventory
    }

    private void setStack(final ItemStack stack, final StackMode mode) {

        this._stack = stack.copy();
        this._stackMode = mode;
    }

    private ItemStack _stack;
    private StackMode _stackMode;

    //endregion
}
