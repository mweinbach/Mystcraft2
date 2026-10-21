/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.lib.common.extensions.IForgeEntity;

/**
 * Forge calls {@link IForgeEntity#onRemovedFromWorld} at the end of the server's tracking callback, after the entity
 * has left the world's ticking lists. Chunk unloads take that path without {@link Entity#remove}.
 */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class ServerLevelEntityCallbacksMixin {

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void mekanism$removedFromWorld(Entity entity, CallbackInfo callback) {
        ((IForgeEntity) entity).onRemovedFromWorld();
    }
}
