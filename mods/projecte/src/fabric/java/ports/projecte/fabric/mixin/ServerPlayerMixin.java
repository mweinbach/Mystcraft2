/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.lib.common.ForgeHooks;

/**
 * Server players post their own living-death event, then {@link net.minecraft.world.entity.player.Player#die} posts
 * another. Forge does the same.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "die", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/server/level/ServerPlayer;gameEvent(Lnet/minecraft/world/level/gameevent/GameEvent;)V",
          shift = At.Shift.AFTER), cancellable = true)
    private void projecte$serverDeath(DamageSource source, CallbackInfo ci) {
        if (ForgeHooks.onLivingDeath((ServerPlayer) (Object) this, source)) {
            ci.cancel();
        }
    }
}
