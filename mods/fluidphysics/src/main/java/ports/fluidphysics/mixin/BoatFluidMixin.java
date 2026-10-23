/*
 * Fluid Physics - Fabric fluid physics library for Minecraft 1.19.2
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.fluidphysics.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ports.fluidphysics.FluidPhysicsSupport;

/**
 * Gives registered fluids Forge's {@code FluidType.supportsBoating}: boats float, find their water level and their
 * status from them. Fluids that do not support boating keep the vanilla water-only behaviour, as on Forge.
 *
 * <p>Vanilla's water checks still run; a registered boating fluid only adds to them.</p>
 */
@Mixin(Boat.class)
abstract class BoatFluidMixin {

    @WrapOperation(method = {"getWaterLevelAbove", "checkInWater", "isUnderwater", "checkFallDamage"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean fluidphysics$canBoatInFluid(final FluidState state, final TagKey<Fluid> tag,
                                                final Operation<Boolean> original) {
        // Vanilla water is not registered, so a registered-fluid-only answer would break ordinary boats.
        return original.call(state, tag) || (FluidTags.WATER.equals(tag) && FluidPhysicsSupport.isBoatingFluid(state));
    }

    /** Forge refuses an extra passenger while the boat's eye is in a fluid that supports boating. */
    @WrapOperation(method = "canAddPassenger", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/Boat;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean fluidphysics$eyeInBoatingFluid(final Boat boat, final TagKey<Fluid> tag,
                                                   final Operation<Boolean> original) {
        return original.call(boat, tag)
                || (FluidTags.WATER.equals(tag) && FluidPhysicsSupport.isEyeInBoatingFluid(boat));
    }
}
