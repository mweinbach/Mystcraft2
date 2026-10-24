/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.projecte.lib.common.ForgeHooks;
import ports.projecte.lib.common.ForgeMod;

/**
 * The living events ProjectE listens to (attack, hurt and jump) and the step height attribute its Gem boots use.
 * Players override hurt and the damage application, so their hooks live on {@link PlayerMixin}.
 * <p>
 * Only injections are used here: other Forge API layers in a pack (Mekanism's port) patch the same vanilla methods,
 * and injections from both coexist where redirects would not.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    protected abstract void actuallyHurt(DamageSource source, float amount);

    @Unique
    private boolean projecte$livingHurtReentry;

    @Inject(method = "createLivingAttributes", at = @At("RETURN"))
    private static void projecte$forgeAttributes(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
        ForgeMod.registerAttributes();
        cir.getReturnValue().add(ForgeMod.STEP_HEIGHT_ADDITION.get());
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void projecte$livingAttack(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!ForgeHooks.onLivingAttack((LivingEntity) (Object) this, source, amount)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
    private void projecte$livingHurt(DamageSource source, float amount, CallbackInfo ci) {
        if (this.projecte$livingHurtReentry) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isInvulnerableTo(source)) {
            return;
        }
        float hurt = ForgeHooks.onLivingHurt(self, source, amount);
        if (hurt <= 0.0F) {
            ci.cancel();
            return;
        }
        if (hurt != amount) {
            this.projecte$livingHurtReentry = true;
            try {
                this.actuallyHurt(source, hurt);
            } finally {
                this.projecte$livingHurtReentry = false;
            }
            ci.cancel();
        }
    }

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void projecte$livingJump(CallbackInfo ci) {
        ForgeHooks.onLivingJump((LivingEntity) (Object) this);
    }
}
