/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.model.multiblock;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.corelib.block.BlockFacings;
import ports.extremereactors.corelib.client.model.BlockVariantsModelBuilder;
import ports.extremereactors.corelib.client.model.data.ModelData;
import ports.extremereactors.corelib.client.model.data.multiblock.CuboidPartVariantsModelDataCache;
import ports.extremereactors.corelib.client.render.ModRenderHelper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Variant models for the parts of a cuboid multiblock. A part of an assembled machine draws its variant only on the
 * faces that look out of the machine (the {@link CuboidPartVariantsModelDataCache#OUTWARD_FACINGS} model data); its
 * other faces are drawn with the template model, the plating seen from inside the machine.
 */
public class CuboidPartVariantsModelBuilder
        extends BlockVariantsModelBuilder {

    public CuboidPartVariantsModelBuilder(final ResourceLocation templateModelId, final boolean ambientOcclusion) {

        super(ambientOcclusion, true, false);
        this._templateModelId = templateModelId;
    }

    @Override
    public void onRegisterModels(final Consumer<ResourceLocation> additionalModels) {

        super.onRegisterModels(additionalModels);
        additionalModels.accept(this._templateModelId);
    }

    @Override
    protected BakedModel createModel(final int blockId, final BakedModel original, final BakedModel[] variants,
                                     final int defaultVariantIndex, final Map<ResourceLocation, BakedModel> models) {
        return new CuboidVariantsModel(blockId, original, variants, defaultVariantIndex,
                models.getOrDefault(this._templateModelId, ModRenderHelper.getMissingModel(models)));
    }

    private class CuboidVariantsModel
            extends VariantsModel {

        CuboidVariantsModel(final int blockId, final BakedModel original, final BakedModel[] variants,
                            final int defaultVariantIndex, final BakedModel template) {

            super(blockId, original, variants, defaultVariantIndex);
            this._template = template;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable final BlockState state, @Nullable final Direction side,
                                        final RandomSource random, final ModelData data,
                                        @Nullable final RenderType renderType) {

            if (null != side) {

                final BlockFacings outward = CuboidPartVariantsModelDataCache.getOutwardFacings(data);

                if (outward.some() && !outward.isSet(side)) {
                    return ModRenderHelper.getQuads(this._template, state, side, random, ModelData.EMPTY, renderType);
                }
            }

            return super.getQuads(state, side, random, data, renderType);
        }

        private final BakedModel _template;
    }

    private final ResourceLocation _templateModelId;
}
