/*
 * ProjectE 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.lib.common.MinecraftForge;
import ports.projecte.lib.event.entity.EntityEvent;

/**
 * Forge posts {@code EntityConstructing} for every entity; ProjectE only listens for players (to drop the offline
 * knowledge cache before a player's saved data loads), so only players post it here.
 */
@Mixin(Player.class)
public abstract class PlayerConstructionMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void projecte$constructing(Level level, BlockPos pos, float yaw, GameProfile profile, @Nullable ProfilePublicKey key, CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new EntityEvent.EntityConstructing((Entity) (Object) this));
    }
}
