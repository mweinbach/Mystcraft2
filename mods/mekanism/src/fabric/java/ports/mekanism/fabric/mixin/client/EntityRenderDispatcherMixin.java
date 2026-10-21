/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import java.util.Map;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.fabric.FabricLifecycle;
import ports.mekanism.lib.client.event.EntityRenderersEvent;

/**
 * Forge posts {@code EntityRenderersEvent.AddLayers} after the dispatcher rebuilds its renderers. That is where the
 * MekaSuit armor layer and the elytra layer are attached.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Shadow
    private Map<EntityType<?>, EntityRenderer<?>> renderers;

    @Shadow
    private Map<String, EntityRenderer<? extends Player>> playerRenderers;

    @Shadow
    @Final
    private EntityModelSet entityModels;

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void mekanism$addLayers(ResourceManager resourceManager, CallbackInfo ci) {
        FabricLifecycle.postToMods(new EntityRenderersEvent.AddLayers(this.renderers, this.playerRenderers, this.entityModels));
    }
}
