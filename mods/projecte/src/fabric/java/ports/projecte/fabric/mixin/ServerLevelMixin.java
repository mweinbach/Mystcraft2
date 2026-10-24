/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.fabric.EntityJoinGuard;
import ports.projecte.lib.common.MinecraftForge;
import ports.projecte.lib.event.entity.EntityJoinLevelEvent;

/**
 * Forge posts {@link EntityJoinLevelEvent} for a player at the start of {@code addPlayer}, before a duplicate UUID is
 * removed, and skips the post inside {@code addNewEntity}. Cancelling the event leaves the player out.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true)
    private void projecte$playerJoin(ServerPlayer player, CallbackInfo ci) {
        if (MinecraftForge.EVENT_BUS.post(new EntityJoinLevelEvent(player, (ServerLevel) (Object) this))) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "addPlayer", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;addNewEntity(Lnet/minecraft/world/level/entity/EntityAccess;)Z"))
    private boolean projecte$addPlayerWithoutSecondJoin(PersistentEntitySectionManager<EntityAccess> manager, EntityAccess entity,
          Operation<Boolean> original) {
        EntityJoinGuard.SKIP.set(Boolean.TRUE);
        try {
            return original.call(manager, entity);
        } finally {
            EntityJoinGuard.SKIP.set(Boolean.FALSE);
        }
    }
}
