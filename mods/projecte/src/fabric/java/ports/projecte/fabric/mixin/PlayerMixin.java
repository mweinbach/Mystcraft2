/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.projecte.lib.common.ForgeHooks;
import ports.projecte.lib.common.extensions.IForgeItem;
import ports.projecte.lib.event.ForgeEventFactory;

/**
 * The player hooks ProjectE needs: the held item's left-click and sweep box, player ticks, and the attack and hurt
 * events (players override {@code hurt} and {@code actuallyHurt}, so {@link LivingEntityMixin} does not see them).
 * Only injections and argument modifiers are used, so another Forge API layer patching the same methods coexists.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Shadow
    protected abstract void actuallyHurt(DamageSource source, float amount);

    @Unique
    private boolean projecte$playerHurtReentry;

    /** Forge asks the held item before a player attacks; {@code true} from the item cancels the attack. */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void projecte$leftClickEntity(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        ItemStack stack = self.getMainHandItem();
        if (!stack.isEmpty() && ((IForgeItem) stack.getItem()).projecte$onLeftClickEntity(stack, self, target)) {
            ci.cancel();
        }
    }

    /** Forge lets the held item choose the box a sweep attack hits. */
    @ModifyArg(method = "attack", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"), index = 1)
    private AABB projecte$sweepBox(AABB original, @Local(argsOnly = true) Entity target) {
        Player self = (Player) (Object) this;
        ItemStack stack = self.getMainHandItem();
        return stack.isEmpty() ? original : ((IForgeItem) stack.getItem()).projecte$getSweepHitBox(stack, self, target);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void projecte$playerTickStart(CallbackInfo ci) {
        ForgeEventFactory.onPlayerPreTick((Player) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void projecte$playerTickEnd(CallbackInfo ci) {
        ForgeEventFactory.onPlayerPostTick((Player) (Object) this);
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void projecte$playerAttack(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!ForgeHooks.onPlayerAttack((Player) (Object) this, source, amount)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
    private void projecte$playerHurt(DamageSource source, float amount, CallbackInfo ci) {
        if (this.projecte$playerHurtReentry) {
            return;
        }
        Player self = (Player) (Object) this;
        if (self.isInvulnerableTo(source)) {
            return;
        }
        float hurt = ForgeHooks.onLivingHurt(self, source, amount);
        if (hurt <= 0.0F) {
            ci.cancel();
            return;
        }
        if (hurt != amount) {
            this.projecte$playerHurtReentry = true;
            try {
                this.actuallyHurt(source, hurt);
            } finally {
                this.projecte$playerHurtReentry = false;
            }
            ci.cancel();
        }
    }
}
