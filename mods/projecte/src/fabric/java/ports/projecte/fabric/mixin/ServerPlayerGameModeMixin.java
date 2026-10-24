/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import ports.projecte.fabric.PEHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.projecte.lib.common.extensions.IForgeItem;

/**
 * The parts of Forge's {@code ServerPlayerGameMode} patch ProjectE relies on: {@code onBlockStartBreak} for the held
 * item and {@code onDestroyedByPlayer} for ProjectE's own blocks. Everything else stays vanilla so other mods' patches
 * of the same calls keep working.
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
     * Forge removes a broken block through {@code onDestroyedByPlayer}, which runs {@code playerWillDestroy} itself and
     * may refuse the removal (ProjectE's pedestal does in creative mode, dropping its item instead). Only ProjectE's
     * blocks are routed there; for every other block both calls stay vanilla.
     */
    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/Block;playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)V"))
    private void projecte$willDestroyFromHook(Block block, Level level, BlockPos pos, BlockState state, Player player, Operation<Void> original) {
        if (!PEHooks.ours(block)) {
            original.call(block, level, pos, state, player);
        }
    }

    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean projecte$onDestroyedByPlayer(ServerLevel level, BlockPos pos, boolean moving, Operation<Boolean> original) {
        BlockState state = level.getBlockState(pos);
        if (!PEHooks.ours(state.getBlock())) {
            return original.call(level, pos, moving);
        }
        boolean harvest = !this.player.isCreative() && this.player.hasCorrectToolForDrops(state);
        return state.projecte$onDestroyedByPlayer(level, pos, this.player, harvest, level.getFluidState(pos));
    }
}
