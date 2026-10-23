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
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

        this._replacements = replacements;
        this._resources = resources;
        this._loadedBlocks = new HashMap<>();
        this._originals = new HashMap<>();
        this._bakedOriginals = new HashMap<>();
    }

    @Nullable
    @Override
    public UnbakedModel loadModelVariant(final ModelResourceLocation id, final ModelProviderContext context) {

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

            final Optional<Block> block = Registry.BLOCK.getOptional(key);
            final Map<ModelResourceLocation, UnbakedModel> models = block.isPresent() ?
                    BlockStateModels.load(this._resources, key, block.get()) : Map.of();

            this._originals.putAll(models);
            return models;
        });
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

            final UnbakedModel original = this._originals.get(id);

            // vanilla loaded this one: it is the original
            model = null != original ? original.bake(bakery, sprites, BlockModelRotation.X0_Y0, id) : null;

            if (null == model) {
                model = bakeOrMissing(bakery, id);
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
    private final ResourceManager _resources;
    private final Map<ResourceLocation, Map<ModelResourceLocation, UnbakedModel>> _loadedBlocks;
    private final Map<ResourceLocation, UnbakedModel> _originals;
    private final Map<ResourceLocation, BakedModel> _bakedOriginals;
    @Nullable
    private ModelReplacements.Session _session;
}
