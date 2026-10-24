/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ports.projecte.lib.common.extensions.IForgeItem;
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
import ports.projecte.lib.common.ForgeHooks;
import ports.projecte.lib.common.ForgeMod;
import ports.projecte.lib.common.ToolActions;
import ports.projecte.lib.common.extensions.IForgeItemStack;
import ports.projecte.lib.event.ForgeEventFactory;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @Shadow
    protected abstract void actuallyHurt(DamageSource source, float amount);

    @Unique
    private boolean projecte$playerHurtReentry;

    @Unique
    private DamageSource projecte$playerHurtSource;

    @Unique
    private float projecte$playerDamageAmount;

    @Unique
    private boolean projecte$playerDamagePosted;

    @Unique
    private boolean projecte$playerExhaustionPending;

    @Unique
    private float projecte$playerExhaustion;

    @Inject(method = "createAttributes", at = @At("RETURN"))
    private static void projecte$reach(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
        ForgeMod.registerAttributes();
        cir.getReturnValue().add(ForgeMod.REACH_DISTANCE.get());
    }

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
        this.projecte$playerHurtSource = source;
        this.projecte$playerDamagePosted = false;
        this.projecte$playerExhaustionPending = false;
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

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void projecte$deferExhaustion(Player player, float exhaustion) {
        this.projecte$playerExhaustionPending = true;
        this.projecte$playerExhaustion = exhaustion;
    }

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"))
    private void projecte$playerDamage(Player player, float health) {
        float current = player.getHealth();
        float original = current - health;
        float adjusted = ForgeHooks.onLivingDamage(player, this.projecte$playerHurtSource, original);
        this.projecte$playerDamageAmount = adjusted;
        this.projecte$playerDamagePosted = true;
        // Vanilla already counted absorption taken. Forge counts that plus whatever the event removed.
        float reduced = original - adjusted;
        if (reduced > 0.0F && reduced < Float.MAX_VALUE) {
            player.awardStat(Stats.DAMAGE_ABSORBED, Math.round(reduced * 10.0F));
        }
        if (adjusted != 0.0F) {
            if (this.projecte$playerExhaustionPending) {
                player.causeFoodExhaustion(this.projecte$playerExhaustion);
            }
            player.setHealth(current - adjusted);
        }
        this.projecte$playerExhaustionPending = false;
    }

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/damagesource/CombatTracker;recordDamage(Lnet/minecraft/world/damagesource/DamageSource;FF)V"))
    private void projecte$playerCombat(CombatTracker tracker, DamageSource source, float health, float damage) {
        if (!this.projecte$playerDamagePosted) {
            tracker.recordDamage(source, health, damage);
            return;
        }
        if (this.projecte$playerDamageAmount != 0.0F) {
            tracker.recordDamage(source, health, this.projecte$playerDamageAmount);
        }
    }

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/resources/ResourceLocation;I)V"))
    private void projecte$playerDamageStats(Player player, ResourceLocation stat, int amount) {
        if (Stats.DAMAGE_TAKEN.equals(stat) && this.projecte$playerDamagePosted) {
            float adjusted = this.projecte$playerDamageAmount;
            if (adjusted != 0.0F && adjusted < Float.MAX_VALUE) {
                player.awardStat(stat, Math.round(adjusted * 10.0F));
            }
            return;
        }
        player.awardStat(stat, amount);
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void projecte$playerDeath(DamageSource source, CallbackInfo ci) {
        if (ForgeHooks.onLivingDeath((Player) (Object) this, source)) {
            ci.cancel();
        }
    }

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void projecte$breakSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        cir.setReturnValue(ForgeEventFactory.getBreakSpeed(self, state, cir.getReturnValueF(), ForgeHooks.pollBreakPos()));
    }

    /**
     * Forge's 1.19.2 player patch damages any stack that can {@code SHIELD_BLOCK}. Vanilla only damages
     * {@link Items#SHIELD}, so a Mekanism shield would block without losing durability.
     */
    @Redirect(method = "hurtCurrentlyUsedShield", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean projecte$shieldBlock(ItemStack stack, Item item) {
        if (item == Items.SHIELD) {
            return ((IForgeItemStack) (Object) stack).canPerformAction(ToolActions.SHIELD_BLOCK);
        }
        return stack.is(item);
    }

    @Redirect(method = "tryToStartFallFlying", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean projecte$anyElytra(ItemStack stack, Item item) {
        if (item == Items.ELYTRA) {
            return true;
        }
        return stack.is(item);
    }

    @Redirect(method = "tryToStartFallFlying", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ElytraItem;isFlyEnabled(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean projecte$canElytraFly(ItemStack stack) {
        return ((IForgeItemStack) (Object) stack).canElytraFly((Player) (Object) this);
    }
}
