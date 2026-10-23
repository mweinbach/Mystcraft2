/*
 * Fluid Physics - Fabric fluid physics library for Minecraft 1.19.2
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.fluidphysics.mixin;

import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ports.fluidphysics.FluidPhysicsSupport;

/**
 * Makes registered fluids act as Forge's do for living entities: water-like travel, jumping and drowning. Forge routes
 * these through {@code FluidType.canSwim} and {@code FluidType.canDrown}; vanilla only tests the water tag.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityFluidMixin {

    /** Forge's {@code isInWater() || isInFluidType(non-lava)} condition in travel and in the jump logic. */
    @Redirect(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isInWater()Z"))
    private boolean fluidphysics$travelInFluid(final LivingEntity entity) {
        return entity.isInWater() || FluidPhysicsSupport.isInFluidAtFeet(entity);
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isInWater()Z"))
    private boolean fluidphysics$jumpInFluid(final LivingEntity entity) {
        return entity.isInWater() || FluidPhysicsSupport.isInFluidAtFeet(entity);
    }

    /** Forge replaces the water height with the tallest fluid type the entity is in. */
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getFluidHeight(Lnet/minecraft/tags/TagKey;)D"))
    private double fluidphysics$fluidHeight(final LivingEntity entity, final TagKey<Fluid> tag) {

        final double height = entity.getFluidHeight(tag);

        if (!FluidTags.WATER.equals(tag)) {
            return height;
        }

        return Math.max(height, FluidPhysicsSupport.fluidHeight(entity));
    }

    /**
     * Vanilla's drowning branch only runs for water. Forge runs it for every fluid with a non-air type and then checks
     * {@code FluidType.canDrownIn}, and that check ignores {@code canBreatheUnderwater}.
     */
    @Redirect(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean fluidphysics$eyeInFluid(final LivingEntity entity, final TagKey<Fluid> tag) {
        return entity.isEyeInFluid(tag)
                || (FluidTags.WATER.equals(tag) && FluidPhysicsSupport.isEyeInDrownableFluid(entity));
    }

    @Redirect(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;canBreatheUnderwater()Z"))
    private boolean fluidphysics$canDrownInFluid(final LivingEntity entity) {
        return entity.canBreatheUnderwater() && !FluidPhysicsSupport.isEyeInDrownableFluid(entity);
    }
}
