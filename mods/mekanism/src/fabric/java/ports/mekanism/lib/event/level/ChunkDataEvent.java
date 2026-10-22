/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.event.level;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * A chunk's saved data, fired when it is read and written.
 * <p>
 * {@link Load} takes its level from the chunk, which is null for a proto chunk, matching Forge's
 * {@code ChunkAccess.getWorldForge}. {@link Save} is given the chunk map's level when the chunk has none.
 */
public class ChunkDataEvent extends ChunkEvent {

    private final CompoundTag data;

    public ChunkDataEvent(ChunkAccess chunk, LevelAccessor level, CompoundTag data) {
        super(chunk, level);
        this.data = data;
    }

    public CompoundTag getData() {
        return data;
    }

    public static class Load extends ChunkDataEvent {

        private final ChunkStatus.ChunkType status;

        public Load(ChunkAccess chunk, CompoundTag data, ChunkStatus.ChunkType status) {
            super(chunk, chunk instanceof LevelChunk levelChunk ? levelChunk.getLevel() : null, data);
            this.status = status;
        }

        public ChunkStatus.ChunkType getStatus() {
            return status;
        }
    }

    public static class Save extends ChunkDataEvent {

        public Save(ChunkAccess chunk, LevelAccessor level, CompoundTag data) {
            super(chunk, level, data);
        }
    }
}
