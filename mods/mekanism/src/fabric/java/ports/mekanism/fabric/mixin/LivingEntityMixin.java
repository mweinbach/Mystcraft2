/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.common.ForgeHooks;
import ports.mekanism.lib.common.ForgeMod;
import ports.mekanism.lib.common.extensions.IForgeItemStack;

/**
 * Equipment slots, elytra flight, the fall-landing sound, and the living events Forge posts from this class.
 * Players override hurt, death and the damage application, so those hooks live on {@link PlayerMixin}.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    protected int fallFlyTicks;

    @Shadow
    protected abstract void actuallyHurt(DamageSource source, float amount);

    @Unique
    private boolean mekanism$livingHurtReentry;

    @Unique
    private DamageSource mekanism$livingHurtSource;

    @Unique
    private float mekanism$livingDamageAmount;

    @Unique
    private float mekanism$livingDamageOriginal;

    @Unique
    private boolean mekanism$livingDamagePosted;

    @Unique
    private boolean mekanism$fallReentry;

    @Inject(method = "createLivingAttributes", at = @At("RETURN"))
    private static void mekanism$forgeAttributes(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
        ForgeMod.registerAttributes();
        cir.getReturnValue().add(ForgeMod.SWIM_SPEED.get()).add(ForgeMod.STEP_HEIGHT_ADDITION.get());
    }

    @Redirect(method = "jumpInLiquid", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 mekanism$swimJump(Vec3 motion, double x, double y, double z) {
        return motion.add(x, y * mekanism$swimSpeed(), z);
    }

    @Redirect(method = "travel", slice = @Slice(
          from = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isInWater()Z"),
          to = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isInLava()Z")),
          at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"))
    private void mekanism$swimSpeed(LivingEntity entity, float speed, Vec3 travel) {
        entity.moveRelative(speed * mekanism$swimSpeed(), travel);
    }

    @Unique
    private float mekanism$swimSpeed() {
        AttributeInstance swim = ((LivingEntity) (Object) this).getAttribute(ForgeMod.SWIM_SPEED.get());
        return swim == null ? 1.0F : (float) swim.getValue();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void mekanism$livingTick(CallbackInfo ci) {
        if (ForgeHooks.onLivingTick((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void mekanism$livingAttack(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!ForgeHooks.onLivingAttack((LivingEntity) (Object) this, source, amount)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
    private void mekanism$livingHurt(DamageSource source, float amount, CallbackInfo ci) {
        this.mekanism$livingHurtSource = source;
        this.mekanism$livingDamagePosted = false;
        if (this.mekanism$livingHurtReentry) {
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
            this.mekanism$livingHurtReentry = true;
            try {
                this.actuallyHurt(source, hurt);
            } finally {
                this.mekanism$livingHurtReentry = false;
            }
            ci.cancel();
        }
    }

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
    private void mekanism$livingDamage(LivingEntity entity, float health) {
        float current = entity.getHealth();
        float original = current - health;
        float adjusted = ForgeHooks.onLivingDamage(entity, this.mekanism$livingHurtSource, original);
        this.mekanism$livingDamageOriginal = original;
        this.mekanism$livingDamageAmount = adjusted;
        this.mekanism$livingDamagePosted = true;
        if (adjusted != 0.0F) {
            entity.setHealth(current - adjusted);
        }
    }

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/damagesource/CombatTracker;recordDamage(Lnet/minecraft/world/damagesource/DamageSource;FF)V"))
    private void mekanism$livingCombat(CombatTracker tracker, DamageSource source, float health, float damage) {
        if (!this.mekanism$livingDamagePosted) {
            tracker.recordDamage(source, health, damage);
            return;
        }
        if (this.mekanism$livingDamageAmount != 0.0F) {
            tracker.recordDamage(source, health, this.mekanism$livingDamageAmount);
        }
    }

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/entity/LivingEntity;setAbsorptionAmount(F)V", ordinal = 1))
    private void mekanism$livingAbsorptionAfterDamage(LivingEntity entity, float absorption) {
        if (!this.mekanism$livingDamagePosted) {
            entity.setAbsorptionAmount(absorption);
            return;
        }
        if (this.mekanism$livingDamageAmount == 0.0F) {
            return;
        }
        entity.setAbsorptionAmount(absorption + this.mekanism$livingDamageOriginal - this.mekanism$livingDamageAmount);
    }

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/entity/LivingEntity;gameEvent(Lnet/minecraft/world/level/gameevent/GameEvent;)V"))
    private void mekanism$livingDamageGameEvent(LivingEntity entity, GameEvent event) {
        if (!this.mekanism$livingDamagePosted || this.mekanism$livingDamageAmount != 0.0F) {
            entity.gameEvent(event);
        }
    }

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void mekanism$livingFall(float distance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (this.mekanism$fallReentry) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        float[] adjusted = ForgeHooks.onLivingFall(self, distance, multiplier);
        if (adjusted == null) {
            cir.setReturnValue(false);
            return;
        }
        if (adjusted[0] != distance || adjusted[1] != multiplier) {
            this.mekanism$fallReentry = true;
            try {
                cir.setReturnValue(self.causeFallDamage(adjusted[0], adjusted[1], source));
            } finally {
                this.mekanism$fallReentry = false;
            }
        }
    }

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void mekanism$livingJump(CallbackInfo ci) {
        ForgeHooks.onLivingJump((LivingEntity) (Object) this);
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void mekanism$livingDeath(DamageSource source, CallbackInfo ci) {
        if (ForgeHooks.onLivingDeath((LivingEntity) (Object) this, source)) {
            ci.cancel();
        }
    }

    @Inject(method = "getEquipmentSlotForItem", at = @At("HEAD"), cancellable = true)
    private static void mekanism$equipmentSlot(ItemStack stack, CallbackInfoReturnable<EquipmentSlot> cir) {
        EquipmentSlot slot = ((IForgeItemStack) (Object) stack).getEquipmentSlot();
        if (slot != null) {
            cir.setReturnValue(slot);
        }
    }

    @Redirect(method = "updateFallFlying", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean mekanism$anyElytra(ItemStack stack, Item item) {
        if (item != Items.ELYTRA) {
            return stack.is(item);
        }
        return true;
    }

    @Redirect(method = "updateFallFlying", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ElytraItem;isFlyEnabled(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean mekanism$elytraTick(ItemStack stack) {
        LivingEntity self = (LivingEntity) (Object) this;
        return ((IForgeItemStack) (Object) stack).canElytraFly(self) && ((IForgeItemStack) (Object) stack).elytraFlightTick(self, this.fallFlyTicks);
    }

    @Redirect(method = "updateFallFlying", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"))
    private void mekanism$elytraDamageAlreadyApplied(ItemStack stack, int amount, LivingEntity entity, Consumer<LivingEntity> onBroken) {
    }

    @Redirect(method = "updateFallFlying", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/entity/LivingEntity;gameEvent(Lnet/minecraft/world/level/gameevent/GameEvent;)V"))
    private void mekanism$elytraGlideAlreadyEmitted(LivingEntity entity, GameEvent event) {
    }

    @Redirect(method = "playBlockFallSound", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType mekanism$fallSound(BlockState state) {
        LivingEntity self = (LivingEntity) (Object) this;
        BlockPos pos = new BlockPos(self.getX(), self.getY() - 0.2D, self.getZ());
        return state.getSoundType(self.level, pos, self);
    }
}
