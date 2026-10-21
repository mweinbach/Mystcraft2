/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachmentBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.lib.common.MinecraftForge;
import ports.mekanism.lib.common.capabilities.AttachedCapabilities;
import ports.mekanism.lib.common.extensions.IForgeBlockEntity;
import ports.mekanism.lib.event.AttachCapabilitiesEvent;

/**
 * Gives block entities the capabilities attached through {@link AttachCapabilitiesEvent}, saved under Forge's key and
 * invalidated while the block entity is removed. The block entity's model data is its render attachment, which is how
 * Fabric's renderer hands it to the port's models.
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin implements IForgeBlockEntity, AttachedCapabilities.Holder, RenderAttachmentBlockEntity {

    @Unique
    @Nullable
    private AttachedCapabilities mekanism$capabilities;

    @Override
    public AttachedCapabilities mekanism$capabilities() {
        if (mekanism$capabilities == null) {
            AttachCapabilitiesEvent<BlockEntity> event = new AttachCapabilitiesEvent<>(BlockEntity.class, (BlockEntity) (Object) this);
            MinecraftForge.EVENT_BUS.post(event);
            mekanism$capabilities = new AttachedCapabilities(event.getCapabilities(), event.getListeners());
        }
        return mekanism$capabilities;
    }

    @Override
    public Object getRenderAttachmentData() {
        return getModelData();
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void mekanism$saveCapabilities(CompoundTag tag, CallbackInfo ci) {
        CompoundTag capabilities = mekanism$capabilities().serialize();
        if (capabilities != null) {
            tag.put(AttachedCapabilities.TAG_KEY, capabilities);
        }
    }

    @Inject(method = "load", at = @At("HEAD"))
    private void mekanism$loadCapabilities(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(AttachedCapabilities.TAG_KEY, CompoundTag.TAG_COMPOUND)) {
            mekanism$capabilities().deserialize(tag.getCompound(AttachedCapabilities.TAG_KEY));
        }
    }

    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void mekanism$invalidateCapabilities(CallbackInfo ci) {
        invalidateCaps();
        requestModelDataUpdate();
    }

    @Inject(method = "clearRemoved", at = @At("TAIL"))
    private void mekanism$reviveCapabilities(CallbackInfo ci) {
        reviveCaps();
    }
}
