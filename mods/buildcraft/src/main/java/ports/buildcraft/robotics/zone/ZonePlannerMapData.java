package ports.buildcraft.robotics.zone;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.level.Level;

/** Upstream {@code ZonePlannerMapData}: map slices cached for two minutes. */
public abstract class ZonePlannerMapData {
    private final Cache<ZonePlannerMapChunkKey, ZonePlannerMapChunk> data = CacheBuilder.newBuilder().expireAfterWrite(2, TimeUnit.MINUTES).build();

    /** Loads a slice that is not cached yet. A client returns null and asks the server. */
    protected abstract ZonePlannerMapChunk load(Level level, ZonePlannerMapChunkKey key);

    public final ZonePlannerMapChunk getChunk(Level level, ZonePlannerMapChunkKey key) {
        ZonePlannerMapChunk cached = data.getIfPresent(key);
        if (cached != null) return cached;
        ZonePlannerMapChunk loaded = load(level, key);
        if (loaded == null) return null;
        data.put(key, loaded);
        return loaded;
    }

    protected final void store(ZonePlannerMapChunkKey key, ZonePlannerMapChunk chunk) {
        data.put(key, chunk);
    }
}
