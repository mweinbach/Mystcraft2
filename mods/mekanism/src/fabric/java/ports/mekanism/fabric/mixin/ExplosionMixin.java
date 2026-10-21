/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ports.mekanism.lib.common.extensions.IForgeExplosion;

@Mixin(Explosion.class)
public abstract class ExplosionMixin implements IForgeExplosion {

    @Shadow
    private Level level;

    @Redirect(method = "finalizeExplosion", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean mekanism$onBlockExploded(Level level, BlockPos pos, BlockState state, int flags) {
        if (state.is(Blocks.AIR)) {
            BlockState current = level.getBlockState(pos);
            if (!current.isAir()) {
                current.onBlockExploded(level, pos, (Explosion) (Object) this);
                return true;
            }
        }
        return level.setBlock(pos, state, flags);
    }

    @Redirect(method = "finalizeExplosion", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/Block;wasExploded(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;)V"))
    private void mekanism$explodedByHook(Block block, Level level, BlockPos pos, Explosion explosion) {
    }

    @WrapOperation(method = "finalizeExplosion", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/Block;dropFromExplosion(Lnet/minecraft/world/level/Explosion;)Z"))
    private boolean mekanism$canDrop(Block block, Explosion explosion, Operation<Boolean> original, @Local(ordinal = 0) BlockState state,
          @Local(ordinal = 0) BlockPos pos) {
        return state.canDropFromExplosion(this.level, pos, (Explosion) (Object) this);
    }
}
