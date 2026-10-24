/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dust connects through {@code canRedstoneConnectTo}, including up the side of a block, the way Forge's wire patch does.
 */
@Mixin(RedStoneWireBlock.class)
public abstract class RedStoneWireBlockMixin {

    @Shadow
    protected abstract boolean canSurviveOn(BlockGetter level, BlockPos pos, BlockState state);

    @Inject(method = "getConnectingSide(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Lnet/minecraft/world/level/block/state/properties/RedstoneSide;",
          at = @At("HEAD"), cancellable = true)
    private void projecte$connect(BlockGetter level, BlockPos pos, Direction direction, boolean nonNormal,
          CallbackInfoReturnable<RedstoneSide> cir) {
        // The position argument is the dust. The block being connected to is one step in {@code direction}.
        BlockPos neighbor = pos.relative(direction);
        BlockState state = level.getBlockState(neighbor);
        if (nonNormal) {
            boolean sturdySupport = this.canSurviveOn(level, neighbor, state);
            if (sturdySupport && level.getBlockState(neighbor.above()).canRedstoneConnectTo(level, neighbor.above(), null)) {
                cir.setReturnValue(state.isFaceSturdy(level, neighbor, direction.getOpposite()) ? RedstoneSide.UP : RedstoneSide.SIDE);
                return;
            }
        }
        if (state.canRedstoneConnectTo(level, neighbor, direction)) {
            cir.setReturnValue(RedstoneSide.SIDE);
        } else if (state.isRedstoneConductor(level, neighbor)) {
            cir.setReturnValue(RedstoneSide.NONE);
        } else {
            BlockPos below = neighbor.below();
            cir.setReturnValue(level.getBlockState(below).canRedstoneConnectTo(level, below, null) ? RedstoneSide.SIDE : RedstoneSide.NONE);
        }
    }
}
