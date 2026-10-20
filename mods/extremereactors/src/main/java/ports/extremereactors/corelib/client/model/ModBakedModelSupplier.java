/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.model;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import ports.extremereactors.platform.ClientPlatform;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Hands out suppliers of baked models by id. A supplier looks its model up again after each model reload, so it
 * also sees the models that {@link ICustomModelBuilder}s put in place of the original ones.
 */
public class ModBakedModelSupplier {

    public ModBakedModelSupplier() {

        this._suppliers = new ConcurrentHashMap<>();
        this._models = Collections.emptyMap();
        ClientPlatform.backend().onModelBakingCompleted(this::onModelsBaked);
    }

    public Supplier<BakedModel> getOrCreate(final ResourceLocation modelId) {
        return this._suppliers.computeIfAbsent(modelId, CachedModel::new);
    }

    private void onModelsBaked(final Map<ResourceLocation, BakedModel> models) {

        this._models = models;
        ++this._generation;
    }

    private final class CachedModel
            implements Supplier<BakedModel> {

        CachedModel(final ResourceLocation id) {

            this._id = id;
            this._generation = -1;
        }

        @Override
        public BakedModel get() {

            final Map<ResourceLocation, BakedModel> models = ModBakedModelSupplier.this._models;
            final int generation = ModBakedModelSupplier.this._generation;

            if (null == this._model || generation != this._generation) {

                BakedModel model = models.get(this._id);

                if (null == model) {
                    model = models.get(ModelBakery.MISSING_MODEL_LOCATION);
                }

                this._model = model;
                this._generation = generation;
            }

            return this._model;
        }

        private final ResourceLocation _id;
        private BakedModel _model;
        private int _generation;
    }

    private final Map<ResourceLocation, CachedModel> _suppliers;
    private volatile Map<ResourceLocation, BakedModel> _models;
    private volatile int _generation;
}
