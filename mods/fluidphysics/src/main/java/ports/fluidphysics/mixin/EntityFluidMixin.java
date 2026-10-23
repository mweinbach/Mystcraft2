/*
 * Fluid Physics - Fabric fluid physics library for Minecraft 1.19.2
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.fluidphysics.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.fluidphysics.FluidPhysicsSupport;

/**
 * Gives registered fluids Forge's per-tick entity physics: pushed motion, the fall distance modifier, extinguishing and
 * vanilla's two-stage swimming rule. Vanilla only does the first for water and lava, through
 * {@code updateInWaterStateAndDoFluidPushing}, which Forge replaces with a pass over every fluid type.
 *
 * <p>The swimming checks keep vanilla's call and OR the registered-fluid result into it, through MixinExtras so other
 * mods can wrap the same calls.</p>
 */
@Mixin(Entity.class)
abstract class EntityFluidMixin {

    @Inject(method = "updateInWaterStateAndDoFluidPushing", at = @At("TAIL"))
    private void fluidphysics$updateFluids(final CallbackInfoReturnable<Boolean> callback) {
        FluidPhysicsSupport.updateEntity((Entity) (Object) this);
    }

    /** Already swimming: body contact with a swimmable fluid keeps the pose, as Forge's {@code isInFluidType}. */
    @ModifyExpressionValue(method = "updateSwimming", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;isInWater()Z"))
    private boolean fluidphysics$keepSwimming(final boolean inWater) {
        return inWater || FluidPhysicsSupport.isInSwimmableFluid((Entity) (Object) this);
    }

    /**
     * Starting to swim: vanilla needs the eye underwater; registered swimmable fluids do the same through the eye
     * check, and the fluid-at-feet condition below still has to hold.
     */
    @ModifyExpressionValue(method = "updateSwimming", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;isUnderWater()Z"))
    private boolean fluidphysics$startSwimming(final boolean underWater) {
        return underWater || FluidPhysicsSupport.isEyeInSwimmableFluid((Entity) (Object) this);
    }

    /** Vanilla also requires water in the feet block before starting the swim pose. */
    @WrapOperation(method = "updateSwimming", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean fluidphysics$swimmableFluidAtFeet(final FluidState state, final TagKey<Fluid> tag,
                                                      final Operation<Boolean> original) {
        return original.call(state, tag) || (FluidTags.WATER.equals(tag)
                && FluidPhysicsSupport.isSwimmableFluidAtFeet((Entity) (Object) this));
    }
}
