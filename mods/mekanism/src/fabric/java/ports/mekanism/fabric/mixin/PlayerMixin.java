/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.common.ForgeHooks;
import ports.mekanism.lib.common.ForgeMod;
import ports.mekanism.lib.common.extensions.IForgeItemStack;
import ports.mekanism.lib.event.ForgeEventFactory;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @Shadow
    protected abstract void actuallyHurt(DamageSource source, float amount);

    @Unique
    private boolean mekanism$playerHurtReentry;

    @Unique
    private DamageSource mekanism$playerHurtSource;

    @Unique
    private float mekanism$playerDamageAmount;

    @Unique
    private boolean mekanism$playerDamagePosted;

    @Unique
    private boolean mekanism$playerExhaustionPending;

    @Unique
    private float mekanism$playerExhaustion;

    @Inject(method = "createAttributes", at = @At("RETURN"))
    private static void mekanism$reach(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
        ForgeMod.registerAttributes();
        cir.getReturnValue().add(ForgeMod.REACH_DISTANCE.get());
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mekanism$playerTickStart(CallbackInfo ci) {
        ForgeEventFactory.onPlayerPreTick((Player) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void mekanism$playerTickEnd(CallbackInfo ci) {
        ForgeEventFactory.onPlayerPostTick((Player) (Object) this);
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void mekanism$playerAttack(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!ForgeHooks.onPlayerAttack((Player) (Object) this, source, amount)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
    private void mekanism$playerHurt(DamageSource source, float amount, CallbackInfo ci) {
        this.mekanism$playerHurtSource = source;
        this.mekanism$playerDamagePosted = false;
        this.mekanism$playerExhaustionPending = false;
        if (this.mekanism$playerHurtReentry) {
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
            this.mekanism$playerHurtReentry = true;
            try {
                this.actuallyHurt(source, hurt);
            } finally {
                this.mekanism$playerHurtReentry = false;
            }
            ci.cancel();
        }
    }

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void mekanism$deferExhaustion(Player player, float exhaustion) {
        this.mekanism$playerExhaustionPending = true;
        this.mekanism$playerExhaustion = exhaustion;
    }

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"))
    private void mekanism$playerDamage(Player player, float health) {
        float current = player.getHealth();
        float original = current - health;
        float adjusted = ForgeHooks.onLivingDamage(player, this.mekanism$playerHurtSource, original);
        this.mekanism$playerDamageAmount = adjusted;
        this.mekanism$playerDamagePosted = true;
        // Vanilla already counted absorption taken. Forge counts that plus whatever the event removed.
        float reduced = original - adjusted;
        if (reduced > 0.0F && reduced < Float.MAX_VALUE) {
            player.awardStat(Stats.DAMAGE_ABSORBED, Math.round(reduced * 10.0F));
        }
        if (adjusted != 0.0F) {
            if (this.mekanism$playerExhaustionPending) {
                player.causeFoodExhaustion(this.mekanism$playerExhaustion);
            }
            player.setHealth(current - adjusted);
        }
        this.mekanism$playerExhaustionPending = false;
    }

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/damagesource/CombatTracker;recordDamage(Lnet/minecraft/world/damagesource/DamageSource;FF)V"))
    private void mekanism$playerCombat(CombatTracker tracker, DamageSource source, float health, float damage) {
        if (!this.mekanism$playerDamagePosted) {
            tracker.recordDamage(source, health, damage);
            return;
        }
        if (this.mekanism$playerDamageAmount != 0.0F) {
            tracker.recordDamage(source, health, this.mekanism$playerDamageAmount);
        }
    }

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/resources/ResourceLocation;I)V"))
    private void mekanism$playerDamageStats(Player player, ResourceLocation stat, int amount) {
        if (Stats.DAMAGE_TAKEN.equals(stat) && this.mekanism$playerDamagePosted) {
            float adjusted = this.mekanism$playerDamageAmount;
            if (adjusted != 0.0F && adjusted < Float.MAX_VALUE) {
                player.awardStat(stat, Math.round(adjusted * 10.0F));
            }
            return;
        }
        player.awardStat(stat, amount);
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void mekanism$playerDeath(DamageSource source, CallbackInfo ci) {
        if (ForgeHooks.onLivingDeath((Player) (Object) this, source)) {
            ci.cancel();
        }
    }

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void mekanism$breakSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        cir.setReturnValue(ForgeEventFactory.getBreakSpeed(self, state, cir.getReturnValueF(), ForgeHooks.pollBreakPos()));
    }

    @Redirect(method = "tryToStartFallFlying", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean mekanism$anyElytra(ItemStack stack, Item item) {
        if (item == Items.ELYTRA) {
            return true;
        }
        return stack.is(item);
    }

    @Redirect(method = "tryToStartFallFlying", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ElytraItem;isFlyEnabled(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean mekanism$canElytraFly(ItemStack stack) {
        return ((IForgeItemStack) (Object) stack).canElytraFly((Player) (Object) this);
    }
}
