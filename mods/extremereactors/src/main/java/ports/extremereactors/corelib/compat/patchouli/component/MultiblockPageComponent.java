/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.compat.patchouli.component;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import ports.extremereactors.corelib.client.model.data.ModelDataOverride;
import ports.extremereactors.corelib.compat.patchouli.Patchouli;
import vazkii.patchouli.api.IVariable;
import vazkii.patchouli.client.book.page.PageMultiblock;

import javax.annotation.Nullable;
import java.util.function.UnaryOperator;

/**
 * A multiblock preview, as Patchouli's multiblock page shows it. Takes {@code multiblock_id}, {@code name},
 * {@code enable_visualize} and {@code text}. A multiblock registered through {@link Patchouli#registerMultiblock}
 * is drawn with the model data that was registered with it, so that its parts look assembled.
 */
public class MultiblockPageComponent
        extends EmbeddedPageComponent<PageMultiblock> {

    public MultiblockPageComponent() {
        super(PageMultiblock.class);
    }

    @Override
    public void onVariablesAvailable(final UnaryOperator<IVariable> lookup) {

        super.onVariablesAvailable(lookup);

        final JsonElement id = null != this.sourceObject ? this.sourceObject.get("multiblock_id") : null;

        this._multiblockId = null != id ? ResourceLocation.tryParse(lookup.apply(IVariable.wrap(id)).asString()) : null;
    }

    @Override
    protected void renderPage(final PageMultiblock page, final PoseStack matrix, final int mouseX, final int mouseY,
                              final float partialTicks) {
        ModelDataOverride.with(Patchouli.getPreviewModelData(this._multiblockId),
                () -> super.renderPage(page, matrix, mouseX, mouseY, partialTicks));
    }

    @Nullable
    private transient ResourceLocation _multiblockId;
}
