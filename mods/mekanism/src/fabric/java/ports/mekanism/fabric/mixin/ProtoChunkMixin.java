/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ProtoChunk.class)
public abstract class ProtoChunkMixin {

    @Redirect(method = "setBlockState", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/state/BlockState;getLightEmission()I"))
    private int mekanism$light(BlockState state, BlockPos pos, BlockState replacement, boolean isMoving) {
        return state.getLightEmission((BlockGetter) (Object) this, pos);
    }
}
