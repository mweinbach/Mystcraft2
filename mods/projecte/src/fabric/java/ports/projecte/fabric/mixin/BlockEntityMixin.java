/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.lib.common.MinecraftForge;
import ports.projecte.lib.common.capabilities.AttachedCapabilities;
import ports.projecte.lib.common.extensions.IForgeBlockEntity;
import ports.projecte.lib.event.AttachCapabilitiesEvent;

/**
 * Gives block entities the capabilities attached through {@link AttachCapabilitiesEvent}, saved under Forge's key and
 * invalidated while the block entity is removed. The block entity's model data is its render attachment, which is how
 * Fabric's renderer hands it to the port's models.
 */
@Mixin(value = BlockEntity.class, priority = 1100)
public abstract class BlockEntityMixin implements IForgeBlockEntity, AttachedCapabilities.Holder {

    @Unique
    @Nullable
    private AttachedCapabilities projecte$capabilities;

    @Override
    public AttachedCapabilities projecte$capabilities() {
        if (projecte$capabilities == null) {
            AttachCapabilitiesEvent<BlockEntity> event = new AttachCapabilitiesEvent<>(BlockEntity.class, (BlockEntity) (Object) this);
            MinecraftForge.EVENT_BUS.post(event);
            projecte$capabilities = new AttachedCapabilities(event.getCapabilities(), event.getListeners());
        }
        return projecte$capabilities;
    }


    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void projecte$saveCapabilities(CompoundTag tag, CallbackInfo ci) {
        projecte$capabilities().saveInto(tag);
    }

    @Inject(method = "load", at = @At("HEAD"))
    private void projecte$loadCapabilities(CompoundTag tag, CallbackInfo ci) {
        projecte$capabilities().loadFrom(tag);
    }

    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void projecte$invalidateCapabilities(CallbackInfo ci) {
        projecte$invalidateCaps();
        projecte$requestModelDataUpdate();
    }

    @Inject(method = "clearRemoved", at = @At("TAIL"))
    private void projecte$reviveCapabilities(CallbackInfo ci) {
        projecte$reviveCaps();
    }
}
