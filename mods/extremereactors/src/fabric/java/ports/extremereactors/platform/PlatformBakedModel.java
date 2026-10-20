/*
 * Extreme Reactors 1.19.2 port - loader services
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.platform;

import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.corelib.client.model.data.ModelData;
import ports.extremereactors.corelib.client.model.data.ModelDataOverride;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * Fabric side of the baked model base: reads the corelib model data from the block entity render attachment.
 * The Forge source set has a class with the same name and API.
 */
public abstract class PlatformBakedModel implements BakedModel, FabricBakedModel {

    public abstract List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                             ModelData data, @Nullable RenderType renderType);

    public TextureAtlasSprite getParticleIcon(final ModelData data) {
        return this.getParticleIcon();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable final BlockState state, @Nullable final Direction side,
                                    final RandomSource random) {
        return this.getQuads(state, side, random, ModelData.EMPTY, null);
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(final BlockAndTintGetter view, final BlockState state, final BlockPos position,
                               final Supplier<RandomSource> random, final RenderContext context) {

        final ModelData override = ModelDataOverride.get(state);
        final Object attachment = null != override ? override : view instanceof RenderAttachedBlockView attached ?
                attached.getBlockEntityRenderAttachment(position) : null;

        context.fallbackConsumer().accept(new Bound(this, attachment instanceof ModelData data ? data : ModelData.EMPTY));
    }

    @Override
    public void emitItemQuads(final ItemStack stack, final Supplier<RandomSource> random, final RenderContext context) {
        context.fallbackConsumer().accept(new Bound(this, ModelData.EMPTY));
    }

    /**
     * A vanilla view of the model for one set of model data, rendered by the Fabric renderer's vanilla path.
     */
    private record Bound(PlatformBakedModel model, ModelData data) implements BakedModel {

        @Override
        public List<BakedQuad> getQuads(@Nullable final BlockState state, @Nullable final Direction side,
                                        final RandomSource random) {
            return this.model.getQuads(state, side, random, this.data, null);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return this.model.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return this.model.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return this.model.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return this.model.isCustomRenderer();
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return this.model.getParticleIcon(this.data);
        }

        @Override
        public ItemTransforms getTransforms() {
            return this.model.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return this.model.getOverrides();
        }
    }
}
