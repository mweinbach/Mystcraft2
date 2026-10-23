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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ports.fluidphysics.FluidPhysicsSupport;

/**
 * Makes registered fluids act as Forge's do for living entities: water-like travel, jumping and drowning. Forge routes
 * these through {@code FluidType.canSwim} and {@code FluidType.canDrown}; vanilla only tests the water tag.
 *
 * <p>Each handler keeps vanilla's call and combines the registered-fluid result with it, through MixinExtras so other
 * mods can wrap the same calls.</p>
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityFluidMixin {

    /** Forge's {@code isInWater() || isInFluidType(non-lava)} condition in travel and in the jump logic. */
    @ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isInWater()Z"))
    private boolean fluidphysics$travelInFluid(final boolean inWater) {
        return inWater || FluidPhysicsSupport.isInFluidAtFeet((LivingEntity) (Object) this);
    }

    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isInWater()Z"))
    private boolean fluidphysics$jumpInFluid(final boolean inWater) {
        return inWater || FluidPhysicsSupport.isInFluidAtFeet((LivingEntity) (Object) this);
    }

    /** Forge replaces the water height with the tallest fluid type the entity is in. */
    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getFluidHeight(Lnet/minecraft/tags/TagKey;)D"))
    private double fluidphysics$fluidHeight(final LivingEntity entity, final TagKey<Fluid> tag,
                                            final Operation<Double> original) {

        final double height = original.call(entity, tag);

        if (!FluidTags.WATER.equals(tag)) {
            return height;
        }

        return Math.max(height, FluidPhysicsSupport.fluidHeight(entity));
    }

    /**
     * Vanilla's drowning branch only runs for water. Forge runs it for every fluid with a non-air type and then checks
     * {@code FluidType.canDrownIn}, and that check ignores {@code canBreatheUnderwater}.
     */
    @WrapOperation(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean fluidphysics$eyeInFluid(final LivingEntity entity, final TagKey<Fluid> tag,
                                            final Operation<Boolean> original) {
        return original.call(entity, tag)
                || (FluidTags.WATER.equals(tag) && FluidPhysicsSupport.isEyeInDrownableFluid(entity));
    }

    @ModifyExpressionValue(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;canBreatheUnderwater()Z"))
    private boolean fluidphysics$canDrownInFluid(final boolean canBreatheUnderwater) {
        return canBreatheUnderwater && !FluidPhysicsSupport.isEyeInDrownableFluid((LivingEntity) (Object) this);
    }
}
