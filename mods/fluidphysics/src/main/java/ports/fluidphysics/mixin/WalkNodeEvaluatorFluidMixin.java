/*
 * Fluid Physics - Fabric fluid physics library for Minecraft 1.19.2
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.fluidphysics.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.fluidphysics.FluidPhysicsSupport;

/**
 * Applies the registered {@code pathType}, as Forge's {@code FluidType.pathType} does before its vanilla water check,
 * and the registered {@code adjacentPathType} to walkable blocks next to the fluid.
 */
@Mixin(WalkNodeEvaluator.class)
abstract class WalkNodeEvaluatorFluidMixin {

    @Inject(method = "getBlockPathTypeRaw", at = @At("HEAD"), cancellable = true)
    private static void fluidphysics$fluidPathType(final BlockGetter level, final BlockPos position,
                                                   final CallbackInfoReturnable<BlockPathTypes> callback) {

        final BlockPathTypes type = FluidPhysicsSupport.pathType(level.getFluidState(position));

        if (null != type) {
            callback.setReturnValue(type);
        }
    }

    /**
     * Forge checks each neighbour's {@code FluidType.adjacentPathType} as the loop reaches it, before vanilla's cactus,
     * berry bush, fire and water checks for that neighbour. At this call the position already holds the neighbour.
     */
    @Inject(method = "checkNeighbourBlocks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"), cancellable = true)
    private static void fluidphysics$adjacentFluidPathType(final BlockGetter level,
                                                           final BlockPos.MutableBlockPos neighbour,
                                                           final BlockPathTypes type,
                                                           final CallbackInfoReturnable<BlockPathTypes> callback) {

        final BlockPathTypes adjacent = FluidPhysicsSupport.adjacentPathType(level.getFluidState(neighbour));

        if (null != adjacent) {
            callback.setReturnValue(adjacent);
        }
    }
}
