/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.event;

import java.util.Map;
import java.util.Set;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import ports.projecte.lib.client.model.geometry.GeometryLoaderManager;
import ports.projecte.lib.client.model.geometry.IGeometryLoader;
import ports.projecte.lib.eventbus.api.Event;

public abstract class ModelEvent extends Event {

    protected ModelEvent() {
    }

    /**
     * Fired on the mod buses after all models are baked and before they are handed to the block and item model
     * shapers. The model map may be modified.
     */
    public static class BakingCompleted extends ModelEvent {

        private final ModelManager modelManager;
        private final Map<ResourceLocation, BakedModel> models;
        private final ModelBakery modelBakery;

        public BakingCompleted(ModelManager modelManager, Map<ResourceLocation, BakedModel> models, ModelBakery modelBakery) {
            this.modelManager = modelManager;
            this.models = models;
            this.modelBakery = modelBakery;
        }

        public ModelManager getModelManager() {
            return modelManager;
        }

        public Map<ResourceLocation, BakedModel> getModels() {
            return models;
        }

        public ModelBakery getModelBakery() {
            return modelBakery;
        }
    }

    /**
     * Fired on the mod buses while the model bakery loads models, to request models that no block state or item
     * references.
     */
    public static class RegisterAdditional extends ModelEvent {

        private final Set<ResourceLocation> models;

        public RegisterAdditional(Set<ResourceLocation> models) {
            this.models = models;
        }

        public void register(ResourceLocation model) {
            models.add(model);
        }
    }

    /**
     * Fired once on the mod buses during client setup. Loaders are registered in the namespace of the bus' mod.
     */
    public static class RegisterGeometryLoaders extends ModelEvent {

        private final String namespace;

        public RegisterGeometryLoaders(String namespace) {
            this.namespace = namespace;
        }

        public void register(String name, IGeometryLoader<?> loader) {
            GeometryLoaderManager.register(new ResourceLocation(namespace, name), loader);
        }
    }
}
