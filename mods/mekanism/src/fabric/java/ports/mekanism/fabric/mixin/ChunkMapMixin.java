/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ports.mekanism.lib.common.MinecraftForge;
import ports.mekanism.lib.event.level.ChunkDataEvent;

/**
 * Forge posts {@link ChunkDataEvent.Save} from {@code ChunkMap.save} after {@link ChunkSerializer#write} and before
 * that tag is written. The level is the chunk's world, or this chunk map's level when the chunk has none.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

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
}
