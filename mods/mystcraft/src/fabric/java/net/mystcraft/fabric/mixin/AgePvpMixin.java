package net.mystcraft.fabric.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.mystcraft.minecraft.age.AgeServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Classic SymbolAntiPvP blocks player-attributed damage only inside the authored Age. */
@Mixin(ServerPlayer.class)
public abstract class AgePvpMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void mystcraft$preventPlayerDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> result) {
        ServerPlayer target = (ServerPlayer) (Object) this;
        if (target.level instanceof AgeServerLevel age && !age.isPvpEnabled() && source.getEntity() instanceof Player)
            result.setReturnValue(false);
    }
}
