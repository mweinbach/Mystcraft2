/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.projecte.lib.common.MinecraftForge;
import ports.projecte.lib.event.level.ChunkDataEvent;

@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerMixin {

    @WrapOperation(method = "read", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/state/BlockState;getLightEmission()I"))
    private static int projecte$savedLight(BlockState state, Operation<Integer> original, @Local BlockPos pos, @Local ChunkAccess chunk) {
        return state.getLightEmission(chunk, pos);
    }

    /**
     * Forge posts {@link ChunkDataEvent.Load} at both returns of {@code read}, with the chunk it built. A full chunk
     * is posted as that {@code LevelChunk}, before vanilla wraps it in an {@link ImposterProtoChunk}.
     */
    @Inject(method = "read", at = @At("RETURN"))
    private static void projecte$chunkLoad(ServerLevel level, PoiManager poiManager, ChunkPos pos, CompoundTag tag,
          CallbackInfoReturnable<ProtoChunk> cir) {
        ProtoChunk returned = cir.getReturnValue();
        if (returned == null) {
            return;
        }
        ChunkAccess chunk = returned instanceof ImposterProtoChunk imposter ? imposter.getWrapped() : returned;
        MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk, tag, chunk.getStatus().getChunkType()));
    }
}
