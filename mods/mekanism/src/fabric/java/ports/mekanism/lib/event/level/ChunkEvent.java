/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.event.level;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;

public class ChunkEvent extends LevelEvent {

    private final ChunkAccess chunk;

    public ChunkEvent(ChunkAccess chunk, LevelAccessor level) {
        super(level);
        this.chunk = chunk;
    }

    public ChunkAccess getChunk() {
        return chunk;
    }

    public static class Load extends ChunkEvent {

        public Load(ChunkAccess chunk, LevelAccessor level) {
            super(chunk, level);
        }
    }

    public static class Unload extends ChunkEvent {

        public Unload(ChunkAccess chunk, LevelAccessor level) {
            super(chunk, level);
        }
    }
}
