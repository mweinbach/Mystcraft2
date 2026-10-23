/*
 * Fluid Physics - Fabric fluid physics library for Minecraft 1.19.2
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.fluidphysics.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.fluidphysics.FluidPhysicsSupport;

/**
 * Gives registered fluids Forge's per-tick entity physics: pushed motion and the fall distance modifier. Vanilla only
 * does this for water and lava, through {@code updateInWaterStateAndDoFluidPushing}, which Forge replaces with a pass
 * over every fluid type.
 */
@Mixin(Entity.class)
abstract class EntityFluidMixin {

    @Inject(method = "updateInWaterStateAndDoFluidPushing", at = @At("TAIL"))
    private void fluidphysics$updateFluids(final CallbackInfoReturnable<Boolean> callback) {
        FluidPhysicsSupport.updateEntity((Entity) (Object) this);
    }

    @Inject(method = "updateSwimming", at = @At("TAIL"))
    private void fluidphysics$swimInFluids(final CallbackInfo callback) {

        final Entity entity = (Entity) (Object) this;

        if (entity.isSprinting() && !entity.isPassenger()
                && (FluidPhysicsSupport.isInSwimmableFluid(entity)
                || FluidPhysicsSupport.isEyeInSwimmableFluid(entity))) {
            entity.setSwimming(true);
        }
    }
}
