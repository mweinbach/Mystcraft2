/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

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

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"))
    private void mekanism$playerDamage(Player player, float health) {
        float current = player.getHealth();
        float adjusted = ForgeHooks.onLivingDamage(player, this.mekanism$playerHurtSource, current - health);
        if (adjusted != 0.0F) {
            player.setHealth(current - adjusted);
        }
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
