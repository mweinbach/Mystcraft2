/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Remembers the loaded chunks of a level that were already looked up, to speed up repeated block entity lookups
 * over a large area. Call {@link #clear()} when done: the cache must not outlive the current tick.
 */
public final class ChunkCache {

    public static ChunkCache getOrCreate(final Level level) {

        synchronized (s_caches) {
            return s_caches.computeIfAbsent(level, ChunkCache::new);
        }
    }

    public Level getLevel() {
        return this._level;
    }

    @Nullable
    public LevelChunk getChunk(final BlockPos position) {

        final int chunkX = SectionPos.blockToSectionCoord(position.getX());
        final int chunkZ = SectionPos.blockToSectionCoord(position.getZ());
        final long key = ChunkPos.asLong(chunkX, chunkZ);
        LevelChunk chunk = this._chunks.get(key);

        if (null == chunk && this._level.hasChunk(chunkX, chunkZ)) {

            chunk = this._level.getChunk(chunkX, chunkZ);
            this._chunks.put(key, chunk);
        }

        return chunk;
    }

    @Nullable
    public BlockEntity getLoadedBlockEntity(final BlockPos position) {

        final LevelChunk chunk = this.getChunk(position);

        return null != chunk ? chunk.getBlockEntity(position, LevelChunk.EntityCreationType.CHECK) : null;
    }

    public void clear() {
        this._chunks.clear();
    }

    //region internals

    private ChunkCache(final Level level) {

        this._level = level;
        this._chunks = new Long2ObjectOpenHashMap<>(16);
    }

    private static final Map<Level, ChunkCache> s_caches = new WeakHashMap<>();

    private final Level _level;
    private final Long2ObjectMap<LevelChunk> _chunks;

    //endregion
}
