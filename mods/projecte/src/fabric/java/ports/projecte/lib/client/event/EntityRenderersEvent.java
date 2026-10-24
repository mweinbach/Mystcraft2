/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.event;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.eventbus.api.Event;

public abstract class EntityRenderersEvent extends Event {

    protected EntityRenderersEvent() {
    }

    /**
     * Fired once on the mod buses during client setup; definitions go to Fabric's model layer registry.
     */
    public static class RegisterLayerDefinitions extends EntityRenderersEvent {

        public void registerLayerDefinition(ModelLayerLocation layerLocation, Supplier<LayerDefinition> supplier) {
            EntityModelLayerRegistry.registerModelLayer(layerLocation, supplier::get);
        }
    }

    /**
     * Fired once on the mod buses during client setup; renderers go to Fabric's renderer registries.
     */
    public static class RegisterRenderers extends EntityRenderersEvent {

        public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> entityType, EntityRendererProvider<T> entityRendererProvider) {
            EntityRendererRegistry.register(entityType, entityRendererProvider);
        }

        public <T extends BlockEntity> void registerBlockEntityRenderer(BlockEntityType<? extends T> blockEntityType,
              BlockEntityRendererProvider<T> blockEntityRendererProvider) {
            BlockEntityRendererRegistry.register(blockEntityType, blockEntityRendererProvider);
        }
    }

    /**
     * Fired on the mod buses each time the entity render dispatcher has recreated its renderers.
     */
    public static class AddLayers extends EntityRenderersEvent {

        private final Map<EntityType<?>, EntityRenderer<?>> renderers;
        private final Map<String, EntityRenderer<? extends Player>> skinMap;
        private final EntityModelSet entityModels;

        public AddLayers(Map<EntityType<?>, EntityRenderer<?>> renderers, Map<String, EntityRenderer<? extends Player>> playerRenderers, EntityModelSet entityModels) {
            this.renderers = renderers;
            this.skinMap = playerRenderers;
            this.entityModels = entityModels;
        }

        public Set<String> getSkins() {
            return skinMap.keySet();
        }

        @Nullable
        @SuppressWarnings("unchecked")
        public <R extends LivingEntityRenderer<? extends Player, ? extends EntityModel<? extends Player>>> R getSkin(String skinName) {
            return (R) skinMap.get(skinName);
        }

        @Nullable
        @SuppressWarnings("unchecked")
        public <T extends LivingEntity, R extends LivingEntityRenderer<T, ? extends EntityModel<T>>> R getRenderer(EntityType<? extends T> entityType) {
            return (R) renderers.get(entityType);
        }

        public EntityModelSet getEntityModels() {
            return entityModels;
        }
    }
}
