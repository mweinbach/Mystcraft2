/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.common.ForgeHooks;

/**
 * Vanilla asks {@code Player.getDestroySpeed} without a position. Forge's dig speed receives the position
 * {@code getDestroyProgress} is measuring, which blasting tools need.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {

    @Inject(method = "getDestroyProgress", at = @At("HEAD"))
    private void mekanism$breakPos(Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        ForgeHooks.setBreakPos(pos);
    }

    @Inject(method = "getDestroyProgress", at = @At("RETURN"))
    private void mekanism$clearBreakPos(Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        ForgeHooks.clearBreakPos();
    }
}
