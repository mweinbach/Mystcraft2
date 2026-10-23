/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.common.MinecraftForge;
import ports.mekanism.lib.event.level.ChunkDataEvent;
import ports.mekanism.lib.event.level.ChunkTicketLevelUpdatedEvent;

/**
 * Forge posts {@link ChunkDataEvent.Save} from {@code ChunkMap.save} after {@link ChunkSerializer#write} and before
 * that tag is written. The level is the chunk's world, or this chunk map's level when the chunk has none.
 * <p>
 * Forge also posts {@link ChunkTicketLevelUpdatedEvent} at the end of {@code ChunkMap.updateChunkScheduling}, after
 * the holder is created or reused, but not from the early return taken when both levels are beyond
 * {@code MAX_CHUNK_DISTANCE}. {@code ForgeEventFactory.fireChunkTicketLevelUpdated} only posts when the level changed.
 * The holder is the one the method returns, which is the local Forge passes.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Shadow
    @Final
    public static int MAX_CHUNK_DISTANCE;

    @Shadow
    @Final
    ServerLevel level;

    @WrapOperation(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/chunk/storage/ChunkSerializer;write(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag mekanism$chunkSave(ServerLevel level, ChunkAccess chunk, Operation<CompoundTag> original) {
        CompoundTag data = original.call(level, chunk);
        LevelAccessor world = chunk instanceof LevelChunk levelChunk ? levelChunk.getLevel() : null;
        if (world == null) {
            world = level;
        }
        MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, world, data));
        return data;
    }

    @Inject(method = "updateChunkScheduling(JILnet/minecraft/server/level/ChunkHolder;I)Lnet/minecraft/server/level/ChunkHolder;",
          at = @At("RETURN"))
    private void mekanism$ticketLevelUpdated(long chunkPos, int newLevel, @Nullable ChunkHolder holder, int oldLevel,
          CallbackInfoReturnable<ChunkHolder> cir) {
        if (oldLevel > MAX_CHUNK_DISTANCE && newLevel > MAX_CHUNK_DISTANCE) {
            //Vanilla's early return, which Forge leaves without posting
            return;
        }
        if (oldLevel != newLevel) {
            MinecraftForge.EVENT_BUS.post(new ChunkTicketLevelUpdatedEvent(level, chunkPos, oldLevel, newLevel, cir.getReturnValue()));
        }
    }
}
