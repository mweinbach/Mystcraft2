/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.model;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Takes part in model loading: names the extra models it needs and replaces baked models once baking is done.
 */
public interface ICustomModelBuilder {

    /**
     * @param additionalModels accepts the id of each extra model to load and bake
     */
    void onRegisterModels(Consumer<ResourceLocation> additionalModels);

    /**
     * @param models the baked top-level models, by model id; entries may be replaced
     */
    void onBakeModels(Map<ResourceLocation, BakedModel> models);
}
