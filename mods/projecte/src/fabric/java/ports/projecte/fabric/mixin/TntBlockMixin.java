/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Forge routes every TNT prime through {@code onCaughtFire}. Obsidian TNT overrides that and spawns its own entity.
 */
@Mixin(TntBlock.class)
public abstract class TntBlockMixin {

    @Redirect(method = "onPlace", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/TntBlock;explode(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void projecte$primeOnPlace(Level level, BlockPos pos, BlockState state, Level placedLevel, BlockPos placedPos, BlockState oldState,
          boolean isMoving) {
        state.onCaughtFire(level, pos, null, null);
    }

    @Redirect(method = "neighborChanged", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/TntBlock;explode(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void projecte$primeOnSignal(Level level, BlockPos pos, BlockState state, Level signalLevel, BlockPos signalPos, Block block,
          BlockPos fromPos, boolean isMoving) {
        state.onCaughtFire(level, pos, null, null);
    }

    @Redirect(method = "playerWillDestroy", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/TntBlock;explode(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void projecte$primeOnDestroy(Level level, BlockPos pos, Level destroyedLevel, BlockPos destroyedPos, BlockState state, Player player) {
        state.onCaughtFire(level, pos, null, null);
    }

    @Redirect(method = "use", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/TntBlock;explode(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void projecte$primeOnUse(Level level, BlockPos pos, LivingEntity igniter, BlockState state, Level useLevel, BlockPos usePos, Player player,
          InteractionHand hand, BlockHitResult hit) {
        state.onCaughtFire(level, pos, hit.getDirection(), igniter);
    }

    @Redirect(method = "onProjectileHit", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/TntBlock;explode(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void projecte$primeOnProjectile(Level level, BlockPos pos, LivingEntity igniter, Level hitLevel, BlockState state, BlockHitResult hit,
          Projectile projectile) {
        state.onCaughtFire(level, pos, null, igniter);
    }
}
