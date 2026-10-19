package com.technovision.advancedgenetics.mixin.fabric;

import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.events.ComponentEvents;
import com.technovision.advancedgenetics.events.GeneticsEvents;
import com.technovision.advancedgenetics.fabric.GeneticsHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Player genetics storage plus the player hooks Forge provides as events. Each injection sits
 * where Forge fires the matching event, so both loaders run the shared handlers at the same point.
 */
@Mixin(Player.class)
public abstract class PlayerFabricMixin implements GeneticsHolder {

    @Unique
    private static final String LEGACY_COMPONENTS_KEY = "cardinal_components";

    @Unique
    private PlayerGeneticsComponent advancedgenetics$genetics;

    @Override
    public PlayerGeneticsComponent advancedgenetics$genetics() {
        if (advancedgenetics$genetics == null) {
            advancedgenetics$genetics = new PlayerGeneticsComponent((Player) (Object) this);
        }
        return advancedgenetics$genetics;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void advancedgenetics$saveGenetics(CompoundTag tag, CallbackInfo ci) {
        tag.put(SAVE_KEY, advancedgenetics$genetics().serializeNBT());
    }

    /** Also reads upstream Fabric saves, which kept the same compound under Cardinal Components. */
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void advancedgenetics$loadGenetics(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(SAVE_KEY, Tag.TAG_COMPOUND)) {
            advancedgenetics$genetics().deserializeNBT(tag.getCompound(SAVE_KEY));
            return;
        }
        CompoundTag legacy = tag.getCompound(LEGACY_COMPONENTS_KEY);
        if (legacy.contains(SAVE_KEY, Tag.TAG_COMPOUND)) {
            advancedgenetics$genetics().deserializeNBT(legacy.getCompound(SAVE_KEY));
        }
    }

    /** Forge's LivingAttackEvent for players fires at the head of {@code Player.hurt}. */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void advancedgenetics$onAttacked(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player && GeneticsEvents.onPlayerAttacked(player, source, amount)) {
            cir.setReturnValue(false);
        }
    }

    /** Forge's AttackEntityEvent fires at the head of {@code Player.attack}. */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void advancedgenetics$onAttackEntity(Entity target, CallbackInfo ci) {
        if (GeneticsEvents.onAttackEntity((Player) (Object) this, target)) {
            ci.cancel();
        }
    }

    /** Forge's EntityInteract fires in {@code Player.interactOn} once the spectator branch is ruled out. */
    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void advancedgenetics$onEntityInteract(Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Player player = (Player) (Object) this;
        if (!player.isSpectator() && GeneticsEvents.onEntityInteract(player, player.level, hand, target)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }

    /** Forge's server PlayerTickEvent END fires at the end of {@code Player.tick}. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void advancedgenetics$serverTick(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (!player.level.isClientSide()) {
            ComponentEvents.serverTick(player);
        }
    }
}
