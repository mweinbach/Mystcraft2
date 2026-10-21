/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.event.level;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * A chunk's saved data, fired when it is read and written.
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

        public Load(ChunkAccess chunk, LevelAccessor level, CompoundTag data) {
            super(chunk, level, data);
        }
    }

    public static class Save extends ChunkDataEvent {

        public Save(ChunkAccess chunk, LevelAccessor level, CompoundTag data) {
            super(chunk, level, data);
        }
    }
}
