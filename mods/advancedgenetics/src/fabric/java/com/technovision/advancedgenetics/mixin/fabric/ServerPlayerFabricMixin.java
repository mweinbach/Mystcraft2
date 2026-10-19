package com.technovision.advancedgenetics.mixin.fabric;

import com.technovision.advancedgenetics.events.GeneticsEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge's LivingDeathEvent for server players fires at the start of {@code ServerPlayer.die}. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerFabricMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void advancedgenetics$onDeath(DamageSource source, CallbackInfo ci) {
        GeneticsEvents.onPlayerDeath((ServerPlayer) (Object) this);
    }
}
