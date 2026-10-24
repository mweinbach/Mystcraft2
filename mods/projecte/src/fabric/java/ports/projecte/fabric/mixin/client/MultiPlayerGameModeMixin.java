/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import ports.projecte.fabric.PEHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.projecte.lib.common.extensions.IForgeItem;

/**
 * The client predicts the same break hooks the server applies. Left-click events stay unwired.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Shadow
    private Minecraft minecraft;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void projecte$startBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Player player = this.minecraft.player;
        if (player == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (((IForgeItem) stack.getItem()).projecte$onBlockStartBreak(stack, pos, player)) {
            cir.setReturnValue(false);
        }
    }

    /** As on the server ({@code ServerPlayerGameModeMixin}): only ProjectE's blocks go through {@code onDestroyedByPlayer}. */
    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/Block;playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)V"))
    private void projecte$willDestroyFromHook(Block block, Level level, BlockPos pos, BlockState state, Player player, Operation<Void> original) {
        if (!PEHooks.ours(block)) {
            original.call(block, level, pos, state, player);
        }
    }

    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean projecte$destroyed(Level level, BlockPos pos, BlockState fluidState, int flags, Operation<Boolean> original) {
        BlockState state = level.getBlockState(pos);
        if (!PEHooks.ours(state.getBlock())) {
            return original.call(level, pos, fluidState, flags);
        }
        return state.projecte$onDestroyedByPlayer(level, pos, this.minecraft.player, false, level.getFluidState(pos));
    }
}
