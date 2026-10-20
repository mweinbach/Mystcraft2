/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import ports.extremereactors.corelib.client.gui.IControlContainer;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A plain container. It can paint extra background with a custom painter and check its controls with a validator.
 */
public class Panel
        extends AbstractCompositeControl {

    public Panel(final ModContainerScreen<? extends ModContainer> gui, final String name) {
        super(gui, name);
    }

    public Panel(final ModContainerScreen<? extends ModContainer> gui) {
        this(gui, gui.nextGenericName());
    }

    /** Paint after the background and before the children. */
    public void setCustomBackgroundPainter(@Nullable final BiConsumer<Panel, PoseStack> painter) {
        this._customBackgroundPainter = painter;
    }

    /** Check this panel when the screen is validated; the validator reports each problem it finds. */
    public void setValidator(@Nullable final BiConsumer<IControlContainer, Consumer<Component>> validator) {
        this._validator = validator;
    }

    //region AbstractCompositeControl

    @Override
    public void onPaintBackground(final PoseStack matrix, final float partialTicks, final int mouseX,
                                  final int mouseY) {

        if (null == this._customBackgroundPainter) {

            super.onPaintBackground(matrix, partialTicks, mouseX, mouseY);
            return;
        }

        this.paintOwnBackground(matrix, partialTicks, mouseX, mouseY);
        this._customBackgroundPainter.accept(this, matrix);
        this.paintChildrenBackground(matrix, partialTicks, mouseX, mouseY);
    }

    @Override
    public void validate(final Consumer<Component> errorReport) {

        if (null != this._validator) {
            this._validator.accept(this, errorReport);
        }

        super.validate(errorReport);
    }

    //endregion
    //region internals

    @Nullable
    private BiConsumer<Panel, PoseStack> _customBackgroundPainter;
    @Nullable
    private BiConsumer<IControlContainer, Consumer<Component>> _validator;

    //endregion
}
