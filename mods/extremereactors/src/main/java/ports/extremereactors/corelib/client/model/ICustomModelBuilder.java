/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.model;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Takes part in model loading: names the extra models it needs and replaces baked models once baking is done.
 * <p>
 * The loaders run every builder in the order they were registered, against one map of baked models: each builder
 * sees the replacements of the builders before it.
 */
public interface ICustomModelBuilder {

    /**
     * @param additionalModels accepts the id of each extra model to load and bake
     */
    void onRegisterModels(Consumer<ResourceLocation> additionalModels);

    /**
     * The ids of the models {@link #onBakeModels(Map)} replaces, known before any model loads. Fabric uses them to
     * claim the block state models through its model loading API instead of editing the baked models afterwards.
     */
    Collection<ResourceLocation> getReplacedModels();

    /**
     * @param models the baked top-level models, by model id; replace the {@link #getReplacedModels()} entries
     */
    void onBakeModels(Map<ResourceLocation, BakedModel> models);
}
