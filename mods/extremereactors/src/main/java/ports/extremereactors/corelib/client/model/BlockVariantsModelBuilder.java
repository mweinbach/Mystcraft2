/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.model;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.corelib.client.model.data.GenericProperties;
import ports.extremereactors.corelib.client.model.data.ModelData;
import ports.extremereactors.corelib.client.render.ModRenderHelper;
import ports.extremereactors.platform.PlatformBakedModel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Replaces the baked model of some blocks with one that draws one of several variant models, chosen by the
 * {@link GenericProperties#ID} and {@link GenericProperties#VARIANT_INDEX} model data of the block being drawn.
 * <p>
 * Each block is identified by an id of the caller's choosing (usually a part-type ordinal). Variants are indexed in
 * the order they are added. Data that names this block but an unknown variant draws the default variant; data that
 * does not name this block draws the original block model.
 */
public class BlockVariantsModelBuilder
        implements ICustomModelBuilder {

    public BlockVariantsModelBuilder(final boolean ambientOcclusion, final boolean guiLight3d,
                                     final boolean builtInRenderer) {

        this._ambientOcclusion = ambientOcclusion;
        this._guiLight3d = guiLight3d;
        this._builtInRenderer = builtInRenderer;
        this._blocks = new Int2ObjectLinkedOpenHashMap<>();
    }

    /**
     * @param blockId             the id the block entity puts in {@link GenericProperties#ID}
     * @param blockModelId        the model to replace, usually a block state model
     * @param defaultVariantIndex the variant to draw when the model data names this block but no known variant
     */
    public void addBlock(final int blockId, final ResourceLocation blockModelId, final int defaultVariantIndex) {

        if (null != this._blocks.putIfAbsent(blockId, new BlockEntry(blockId, blockModelId, defaultVariantIndex))) {
            throw new IllegalArgumentException("Block " + blockId + " was already added to this model builder");
        }
    }

    public void addVariant(final int blockId, final ResourceLocation variantModelId) {
        this.getBlock(blockId).variants.add(variantModelId);
    }

    public void addModels(final int blockId, final List<ResourceLocation> variantModelIds) {
        this.getBlock(blockId).variants.addAll(variantModelIds);
    }

    //region ICustomModelBuilder

    @Override
    public void onRegisterModels(final Consumer<ResourceLocation> additionalModels) {

        for (final BlockEntry block : this._blocks.values()) {
            for (final ResourceLocation id : block.variants) {
                if (!(id instanceof ModelResourceLocation)) {
                    additionalModels.accept(id);
                }
            }
        }
    }

    @Override
    public Collection<ResourceLocation> getReplacedModels() {

        final List<ResourceLocation> ids = new ArrayList<>(this._blocks.size());

        for (final BlockEntry block : this._blocks.values()) {
            ids.add(block.blockModelId);
        }

        return ids;
    }

    @Override
    public void onBakeModels(final Map<ResourceLocation, BakedModel> models) {

        final BakedModel missing = ModRenderHelper.getMissingModel(models);
        final List<BakedModel> replacements = new ArrayList<>(this._blocks.size());

        // resolve every variant before replacing anything: a block model is often also its own first variant
        for (final BlockEntry block : this._blocks.values()) {

            final BakedModel[] variants = block.variants.stream()
                    .map(id -> models.getOrDefault(id, missing))
                    .toArray(BakedModel[]::new);

            replacements.add(this.createModel(block.blockId, models.getOrDefault(block.blockModelId, missing),
                    0 == variants.length ? new BakedModel[]{missing} : variants,
                    Math.max(0, Math.min(block.defaultVariantIndex, variants.length - 1)), models));
        }

        int index = 0;

        for (final BlockEntry block : this._blocks.values()) {
            models.put(block.blockModelId, replacements.get(index++));
        }
    }

    //endregion
    //region internals

    /**
     * Create the model that replaces one block model.
     *
     * @param original the block model being replaced
     * @param models   the baked models, before any replacement made by this builder
     */
    protected BakedModel createModel(final int blockId, final BakedModel original, final BakedModel[] variants,
                                     final int defaultVariantIndex, final Map<ResourceLocation, BakedModel> models) {
        return new VariantsModel(blockId, original, variants, defaultVariantIndex);
    }

    private BlockEntry getBlock(final int blockId) {

        final BlockEntry block = this._blocks.get(blockId);

        if (null == block) {
            throw new IllegalArgumentException("Block " + blockId + " was not added to this model builder");
        }

        return block;
    }

    private record BlockEntry(int blockId, ResourceLocation blockModelId, int defaultVariantIndex,
                              List<ResourceLocation> variants) {

        BlockEntry(final int blockId, final ResourceLocation blockModelId, final int defaultVariantIndex) {
            this(blockId, blockModelId, defaultVariantIndex, new ArrayList<>());
        }
    }

    protected class VariantsModel
            extends PlatformBakedModel {

        protected VariantsModel(final int blockId, final BakedModel original, final BakedModel[] variants,
                                final int defaultVariantIndex) {

            this._blockId = blockId;
            this._original = original;
            this._variants = variants;
            this._defaultVariant = variants[defaultVariantIndex];
        }

        protected BakedModel getVariant(final ModelData data) {

            if (!data.has(GenericProperties.ID) || this._blockId != GenericProperties.getId(data)) {
                return this._original;
            }

            final int index = GenericProperties.getVariantIndex(data);

            return index >= 0 && index < this._variants.length ? this._variants[index] : this._defaultVariant;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable final BlockState state, @Nullable final Direction side,
                                        final RandomSource random, final ModelData data,
                                        @Nullable final RenderType renderType) {
            return ModRenderHelper.getQuads(this.getVariant(data), state, side, random, data, renderType);
        }

        @Override
        public TextureAtlasSprite getParticleIcon(final ModelData data) {
            return this.getVariant(data).getParticleIcon();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return BlockVariantsModelBuilder.this._ambientOcclusion;
        }

        @Override
        public boolean isGui3d() {
            return BlockVariantsModelBuilder.this._guiLight3d;
        }

        @Override
        public boolean usesBlockLight() {
            return this._original.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return BlockVariantsModelBuilder.this._builtInRenderer;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return this._original.getParticleIcon();
        }

        @Override
        @SuppressWarnings("deprecation")
        public ItemTransforms getTransforms() {
            return this._original.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return this._original.getOverrides();
        }

        private final int _blockId;
        private final BakedModel _original;
        private final BakedModel[] _variants;
        private final BakedModel _defaultVariant;
    }

    private final boolean _ambientOcclusion;
    private final boolean _guiLight3d;
    private final boolean _builtInRenderer;
    private final Int2ObjectMap<BlockEntry> _blocks;

    //endregion
}
