/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ports.projecte.fabric.PEHooks;

/**
 * Forge primes TNT through {@code onCaughtFire}, which ProjectE's Nova Catalyst and Nova Cataclysm override to spawn
 * their own primed entity. Vanilla calls {@code TntBlock.explode} directly; for ProjectE's blocks that call goes to
 * {@code onCaughtFire} instead. Other TNT blocks keep the vanilla call.
 */
@Mixin(TntBlock.class)
public abstract class TntBlockMixin {

    @WrapOperation(method = {"onPlace", "neighborChanged", "playerWillDestroy"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/TntBlock;explode(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void projecte$prime(Level level, BlockPos pos, Operation<Void> original) {
        if (!projecte$primeOurs(level, pos, null, null)) {
            original.call(level, pos);
        }
    }

    @WrapOperation(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/TntBlock;explode(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void projecte$primeOnUse(Level level, BlockPos pos, LivingEntity igniter, Operation<Void> original, @Local(argsOnly = true) BlockHitResult hit) {
        if (!projecte$primeOurs(level, pos, hit.getDirection(), igniter)) {
            original.call(level, pos, igniter);
        }
    }

    @WrapOperation(method = "onProjectileHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/TntBlock;explode(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void projecte$primeOnProjectile(Level level, BlockPos pos, LivingEntity igniter, Operation<Void> original) {
        if (!projecte$primeOurs(level, pos, null, igniter)) {
            original.call(level, pos, igniter);
        }
    }

    private boolean projecte$primeOurs(Level level, BlockPos pos, @Nullable Direction face, @Nullable LivingEntity igniter) {
        if (!PEHooks.ours(this)) {
            return false;
        }
        level.getBlockState(pos).projecte$onCaughtFire(level, pos, face, igniter);
        return true;
    }
}
