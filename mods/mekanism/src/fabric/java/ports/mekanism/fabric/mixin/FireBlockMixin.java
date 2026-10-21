/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Forge's fire spread calls {@code onCaughtFire} on the block that burned. Vanilla only special-cases {@link TntBlock}
 * and would spawn ordinary primed TNT for obsidian TNT.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    /**
     * Vanilla asks the burn-odds table before it will consume a block. Obsidian TNT is not in that table; Forge reads
     * {@code getFlammability} instead, which this block overrides. The default still consults the table, so blocks that
     * do not override it keep their vanilla odds.
     */
    @WrapOperation(method = "checkBurnOut", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/FireBlock;getBurnOdds(Lnet/minecraft/world/level/block/state/BlockState;)I"))
    private int mekanism$burnOdds(FireBlock fire, BlockState state, Operation<Integer> original, @Local(argsOnly = true) Level level,
          @Local(argsOnly = true) BlockPos pos) {
        return state.getFlammability(level, pos, null);
    }

    @WrapOperation(method = "checkBurnOut", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/TntBlock;explode(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void mekanism$primeBurnedTnt(Level level, BlockPos pos, Operation<Void> original, @Local BlockState state) {
        state.onCaughtFire(level, pos, null, null);
    }
}
