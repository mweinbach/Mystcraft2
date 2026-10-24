/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets {@code isPortalFrame} blocks stand in for obsidian. Wrapped rather than redirected: other mods (Charm in
 * Tekxit 4) redirect the same call, and two redirects of one instruction crash the game at startup.
 */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin {

    @WrapOperation(method = "isPortal", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
    private static boolean mekanism$portalFrame(BlockState state, Block block, Operation<Boolean> original, Level level, BlockPos pos,
          Direction direction) {
        if (block == Blocks.OBSIDIAN && state.isPortalFrame(level, pos)) {
            return true;
        }
        return original.call(state, block);
    }
}
