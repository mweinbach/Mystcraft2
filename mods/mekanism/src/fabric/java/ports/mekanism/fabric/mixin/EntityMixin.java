/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.common.MinecraftForge;
import ports.mekanism.lib.common.capabilities.AttachedCapabilities;
import ports.mekanism.lib.common.extensions.IForgeEntity;
import ports.mekanism.lib.event.AttachCapabilitiesEvent;

/**
 * Gives entities the capabilities attached through {@link AttachCapabilitiesEvent}, saved with the entity under
 * Forge's key, invalidated when the entity is removed and revived when it is brought back.
 */
@Mixin(Entity.class)
public abstract class EntityMixin implements IForgeEntity, AttachedCapabilities.Holder {

    @Unique
    @Nullable
    private AttachedCapabilities mekanism$capabilities;

    @Override
    public AttachedCapabilities mekanism$capabilities() {
        if (mekanism$capabilities == null) {
            AttachCapabilitiesEvent<Entity> event = new AttachCapabilitiesEvent<>(Entity.class, (Entity) (Object) this);
            MinecraftForge.EVENT_BUS.post(event);
            mekanism$capabilities = new AttachedCapabilities(event.getCapabilities(), event.getListeners());
        }
        return mekanism$capabilities;
    }

    @Inject(method = "saveWithoutId", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
                                                 shift = At.Shift.AFTER))
    private void mekanism$saveCapabilities(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag capabilities = mekanism$capabilities().serialize();
        if (capabilities != null) {
            tag.put(AttachedCapabilities.TAG_KEY, capabilities);
        }
    }

    @Inject(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
                                        shift = At.Shift.AFTER))
    private void mekanism$loadCapabilities(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(AttachedCapabilities.TAG_KEY, CompoundTag.TAG_COMPOUND)) {
            mekanism$capabilities().deserialize(tag.getCompound(AttachedCapabilities.TAG_KEY));
        }
    }

    @Inject(method = "remove", at = @At("TAIL"))
    private void mekanism$invalidateCapabilities(Entity.RemovalReason reason, CallbackInfo ci) {
        invalidateCaps();
    }

    @Inject(method = "unsetRemoved", at = @At("TAIL"))
    private void mekanism$reviveCapabilities(CallbackInfo ci) {
        reviveCaps();
    }

    @Redirect(method = "playStepSound", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType mekanism$stepSound(BlockState state, BlockPos pos, BlockState under) {
        Entity self = (Entity) (Object) this;
        return state.getSoundType(self.level, pos, self);
    }
}
