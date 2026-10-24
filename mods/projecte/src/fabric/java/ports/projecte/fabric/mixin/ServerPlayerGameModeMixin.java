/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.projecte.lib.common.ForgeHooks;
import ports.projecte.lib.common.extensions.IForgeItem;

/**
 * Breaks and item use follow Forge's {@code ServerPlayerGameMode} patch: break experience, {@code projecte$onBlockStartBreak},
 * {@code onDestroyedByPlayer}, {@code onItemUseFirst} and sneak bypass. Fabric's break and use callbacks stay on their
 * own injects.
 */
@Mixin(value = ServerPlayerGameMode.class, priority = 500)
public abstract class ServerPlayerGameModeMixin {

    @Shadow
    protected ServerLevel level;
    @Final
    @Shadow
    protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/server/level/ServerPlayer;blockActionRestricted(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/GameType;)Z"),
          cancellable = true)
    private void projecte$onBlockStartBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = this.player.getMainHandItem();
        if (((IForgeItem) stack.getItem()).projecte$onBlockStartBreak(stack, pos, this.player)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Fabric fires its break callback at this call. The real {@code playerWillDestroy} runs from
     * {@code onDestroyedByPlayer}, so this invoke stays in the method and does nothing itself.
     */
    @Redirect(method = "destroyBlock", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/Block;playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)V"))
    private void projecte$keepBreakCallback(Block block, Level level, BlockPos pos, BlockState state, Player player) {
    }

    @Redirect(method = "destroyBlock", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean projecte$onDestroyedByPlayer(ServerLevel level, BlockPos pos, boolean moving) {
        BlockState state = level.getBlockState(pos);
        boolean harvest = !this.player.isCreative() && this.player.hasCorrectToolForDrops(state);
        boolean removed = state.onDestroyedByPlayer(level, pos, this.player, harvest, level.getFluidState(pos));
        if (removed && !this.player.isCreative()) {
            int exp = ForgeHooks.consumeBreakExp();
            if (exp > 0) {
                state.getBlock().popExperience(level, pos, exp);
            }
        }
        return removed;
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void projecte$clearBreakExp(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ForgeHooks.consumeBreakExp();
    }

    @Inject(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isSecondaryUseActive()Z"), cancellable = true)
    private void projecte$onItemUseFirst(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hit,
          CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = ((IForgeItem) stack.getItem()).onItemUseFirst(stack, new UseOnContext(player, hand, hit));
        if (result != InteractionResult.PASS) {
            cir.setReturnValue(result);
        }
    }

    @WrapOperation(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isSecondaryUseActive()Z"))
    private boolean projecte$sneakBypass(ServerPlayer player, Operation<Boolean> original, ServerPlayer serverPlayer, Level level, ItemStack stack,
          InteractionHand hand, BlockHitResult hit) {
        if (!original.call(player)) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        boolean bypass = ((IForgeItem) main.getItem()).doesSneakBypassUse(main, level, pos, player)
              && ((IForgeItem) off.getItem()).doesSneakBypassUse(off, level, pos, player);
        return !bypass;
    }
}
