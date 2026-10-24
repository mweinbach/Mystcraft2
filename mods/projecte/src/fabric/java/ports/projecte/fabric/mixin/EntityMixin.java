/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.projecte.lib.common.ForgeMod;
import ports.projecte.lib.common.MinecraftForge;
import ports.projecte.lib.common.capabilities.AttachedCapabilities;
import ports.projecte.lib.common.extensions.IForgeEntity;
import ports.projecte.lib.event.AttachCapabilitiesEvent;

/**
 * Gives entities the capabilities attached through {@link AttachCapabilitiesEvent}, saved with the entity under
 * Forge's key, invalidated when the entity is removed and revived when it is brought back.
 */
@Mixin(Entity.class)
public abstract class EntityMixin implements IForgeEntity, AttachedCapabilities.Holder {

    /**
     * Forge steps up by {@code maxUpStep} plus the step-height attribute. Free runners and the MekaSuit write that
     * attribute; vanilla only reads the field. This must not be a {@code @Redirect}: other mods (Tekxit's Step Height
     * Entity Attribute) redirect the same read, and two redirects of one instruction crash the game at startup.
     */
    @ModifyExpressionValue(method = "collide", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;maxUpStep:F", opcode = Opcodes.GETFIELD))
    private float projecte$stepHeight(float step) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof LivingEntity living) {
            AttributeInstance attribute = living.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
            if (attribute != null) {
                step += (float) attribute.getValue();
            }
        }
        return step;
    }

    @Unique
    @Nullable
    private AttachedCapabilities projecte$capabilities;

    @Override
    public AttachedCapabilities projecte$capabilities() {
        if (projecte$capabilities == null) {
            AttachCapabilitiesEvent<Entity> event = new AttachCapabilitiesEvent<>(Entity.class, (Entity) (Object) this);
            MinecraftForge.EVENT_BUS.post(event);
            projecte$capabilities = new AttachedCapabilities(event.getCapabilities(), event.getListeners());
        }
        return projecte$capabilities;
    }

    @Inject(method = "saveWithoutId", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
                                                 shift = At.Shift.AFTER))
    private void projecte$saveCapabilities(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag capabilities = projecte$capabilities().serialize();
        if (capabilities != null) {
            tag.put(AttachedCapabilities.TAG_KEY, capabilities);
        }
    }

    @Inject(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
                                        shift = At.Shift.AFTER))
    private void projecte$loadCapabilities(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(AttachedCapabilities.TAG_KEY, CompoundTag.TAG_COMPOUND)) {
            projecte$capabilities().deserialize(tag.getCompound(AttachedCapabilities.TAG_KEY));
        }
    }

    @Inject(method = "remove", at = @At("TAIL"))
    private void projecte$invalidateCapabilities(Entity.RemovalReason reason, CallbackInfo ci) {
        projecte$invalidateCaps();
    }

    @Inject(method = "unsetRemoved", at = @At("TAIL"))
    private void projecte$reviveCapabilities(CallbackInfo ci) {
        projecte$reviveCaps();
    }
}
