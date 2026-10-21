/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerMixin {

    @WrapOperation(method = "read", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/state/BlockState;getLightEmission()I"))
    private static int mekanism$savedLight(BlockState state, Operation<Integer> original, @Local BlockPos pos, @Local ChunkAccess chunk) {
        return state.getLightEmission(chunk, pos);
    }
}
