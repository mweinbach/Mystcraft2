/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.common.extensions.IForgeItem;

/**
 * The client predicts the same break hooks the server applies. Left-click events stay unwired.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Shadow
    private Minecraft minecraft;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void mekanism$startBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Player player = this.minecraft.player;
        if (player == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (((IForgeItem) stack.getItem()).mekanism$onBlockStartBreak(stack, pos, player)) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(method = "destroyBlock", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/Block;playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)V"))
    private void mekanism$willDestroyFromHook(Block block, Level level, BlockPos pos, BlockState state, Player player) {
    }

    @Redirect(method = "destroyBlock", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean mekanism$destroyed(Level level, BlockPos pos, BlockState fluidState, int flags) {
        BlockState state = level.getBlockState(pos);
        Player player = this.minecraft.player;
        FluidState fluid = level.getFluidState(pos);
        return state.onDestroyedByPlayer(level, pos, player, false, fluid);
    }

    @Redirect(method = "continueDestroyBlock", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType mekanism$breakSound(BlockState state, BlockPos pos, Direction direction) {
        return state.getSoundType(this.minecraft.level, pos, this.minecraft.player);
    }

    @Inject(method = "performUseItemOn", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/player/LocalPlayer;isSecondaryUseActive()Z"), cancellable = true)
    private void mekanism$onItemUseFirst(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        InteractionResult result = ((IForgeItem) stack.getItem()).onItemUseFirst(stack, new UseOnContext(player, hand, hit));
        if (result != InteractionResult.PASS) {
            cir.setReturnValue(result);
        }
    }

    @WrapOperation(method = "performUseItemOn", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/player/LocalPlayer;isSecondaryUseActive()Z"))
    private boolean mekanism$sneakBypass(LocalPlayer player, Operation<Boolean> original, LocalPlayer ignored, InteractionHand hand, BlockHitResult hit) {
        if (!original.call(player)) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        boolean bypass = ((IForgeItem) main.getItem()).doesSneakBypassUse(main, player.level, pos, player)
              && ((IForgeItem) off.getItem()).doesSneakBypassUse(off, player.level, pos, player);
        return !bypass;
    }
}
