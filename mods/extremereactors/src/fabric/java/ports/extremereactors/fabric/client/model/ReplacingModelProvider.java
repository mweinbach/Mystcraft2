/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric.client.model;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.model.ModelProviderContext;
import net.fabricmc.fabric.api.client.model.ModelVariantProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Puts the models of the {@link ModelReplacements} in place through Fabric's model loading API, one instance per
 * model reload.
 * <p>
 * It loads every block state model of the blocks that have a replaced model, as vanilla would (see
 * {@link BlockStateModels}), and hands out a {@link ReplacedModel} for the replaced ones. When baked, a replaced
 * model asks the reload's {@link ModelReplacements.Session} for the model its builder made from the baked originals.
 */
public final class ReplacingModelProvider
        implements ModelVariantProvider {

    public ReplacingModelProvider(final ModelReplacements replacements, final ResourceManager resources) {
        this(replacements, blockId -> Registry.BLOCK.getOptional(blockId)
                .map(block -> BlockStateModels.load(resources, blockId, block))
                .orElse(Map.of()));
    }

    /**
     * @param blockModels loads every block state model of a block, as vanilla would
     */
    ReplacingModelProvider(final ModelReplacements replacements,
                           final Function<ResourceLocation, Map<ModelResourceLocation, UnbakedModel>> blockModels) {

        this._replacements = replacements;
        this._blockModels = blockModels;
        this._loadedBlocks = new HashMap<>();
        this._originals = new HashMap<>();
        this._bakedOriginals = new HashMap<>();
    }

    // ModernFix's dynamic resources (on in Tekxit 4) loads and bakes models lazily, from more than one thread
    @Nullable
    @Override
    public synchronized UnbakedModel loadModelVariant(final ModelResourceLocation id, final ModelProviderContext context) {

        final ResourceLocation blockId = new ResourceLocation(id.getNamespace(), id.getPath());

        if ("inventory".equals(id.getVariant()) || !this._replacements.blocks().contains(blockId)) {
            return null;
        }

        final UnbakedModel original = this.loadBlock(blockId).get(id);

        if (null == original) {
            // vanilla then loads the whole block, replaced models included: they show their original models
            LOGGER.warn("No block state model for {}; its replacement, if any, is skipped", id);
            return null;
        }

        return this._replacements.replaces(id) ? new ReplacedModel(id, original) : original;
    }

    private Map<ModelResourceLocation, UnbakedModel> loadBlock(final ResourceLocation blockId) {
        return this._loadedBlocks.computeIfAbsent(blockId, key -> {

            final Map<ModelResourceLocation, UnbakedModel> models = this._blockModels.apply(key);

            this._originals.putAll(models);
            return models;
        });
    }

    /**
     * @return the unbaked original of a replaced model, loading its block first if Minecraft has not asked for it yet.
     * Vanilla loads every model before baking any, but ModernFix's dynamic resources loads them on demand, so a builder
     * can ask for the original of a block nothing has loaded yet.
     */
    @Nullable
    synchronized UnbakedModel originalOf(final ResourceLocation id) {

        if (id instanceof ModelResourceLocation model) {
            this.loadBlock(new ResourceLocation(model.getNamespace(), model.getPath()));
        }

        return this._originals.get(id);
    }

    private ModelReplacements.Session session(final ModelBakery bakery,
                                              final Function<Material, TextureAtlasSprite> sprites) {

        if (null == this._session) {
            this._session = this._replacements.newSession(id -> this.bakeOriginal(bakery, sprites, id),
                    id -> bakeOrMissing(bakery, id));
        }

        return this._session;
    }

    private BakedModel bakeOriginal(final ModelBakery bakery, final Function<Material, TextureAtlasSprite> sprites,
                                    final ResourceLocation id) {

        BakedModel model = this._bakedOriginals.get(id);

        if (null == model) {

            final UnbakedModel original = this.originalOf(id);

            model = null != original ? original.bake(bakery, sprites, BlockModelRotation.X0_Y0, id) : null;

            if (null == model) {
                // Asking the bakery for a replaced id would come back here through ReplacedModel and run the same
                // builder again ("Model builder N depends on itself").
                model = this._replacements.replaces(id) ?
                        bakeOrMissing(bakery, ModelBakery.MISSING_MODEL_LOCATION) : bakeOrMissing(bakery, id);
            }

            this._bakedOriginals.put(id, model);
        }

        return model;
    }

    private static BakedModel bakeOrMissing(final ModelBakery bakery, final ResourceLocation id) {

        final BakedModel model = bakery.bake(id, BlockModelRotation.X0_Y0);

        return null != model ? model : bakery.bake(ModelBakery.MISSING_MODEL_LOCATION, BlockModelRotation.X0_Y0);
    }

    /**
     * Stands for a replaced block state model: it loads the original model's dependencies and textures, and bakes to
     * the replacement.
     */
    private final class ReplacedModel
            implements UnbakedModel {

        ReplacedModel(final ModelResourceLocation id, final UnbakedModel original) {

            this._id = id;
            this._original = original;
        }

        @Override
        public Collection<ResourceLocation> getDependencies() {
            return this._original.getDependencies();
        }

        @Override
        public Collection<Material> getMaterials(final Function<ResourceLocation, UnbakedModel> models,
                                                 final Set<Pair<String, String>> missingTextures) {
            return this._original.getMaterials(models, missingTextures);
        }

        @Override
        public BakedModel bake(final ModelBakery bakery, final Function<Material, TextureAtlasSprite> sprites,
                               final ModelState state, final ResourceLocation location) {

            synchronized (ReplacingModelProvider.this) {
                return ReplacingModelProvider.this.session(bakery, sprites).get(this._id);
            }
        }

        private final ModelResourceLocation _id;
        private final UnbakedModel _original;
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ModelReplacements _replacements;
    private final Function<ResourceLocation, Map<ModelResourceLocation, UnbakedModel>> _blockModels;
    private final Map<ResourceLocation, Map<ModelResourceLocation, UnbakedModel>> _loadedBlocks;
    private final Map<ResourceLocation, UnbakedModel> _originals;
    private final Map<ResourceLocation, BakedModel> _bakedOriginals;
    @Nullable
    private ModelReplacements.Session _session;
}
