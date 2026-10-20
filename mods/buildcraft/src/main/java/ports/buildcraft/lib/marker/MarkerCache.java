package ports.buildcraft.lib.marker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Upstream {@code MarkerCache}: one kind of marker. Server sub-caches live in their level's saved data
 * under upstream's names; client sub-caches are kept per client level.
 */
public final class MarkerCache<S extends MarkerSubCache<?>> {
    public static final List<MarkerCache<?>> CACHES = new ArrayList<>();

    public final String name;
    private final String savedDataName;
    private final Function<Level, S> factory;
    private final Map<Level, S> clientCaches = Collections.synchronizedMap(new WeakHashMap<>());

    public MarkerCache(String name, String savedDataName, Function<Level, S> factory) {
        this.name = name;
        this.savedDataName = savedDataName;
        this.factory = factory;
        CACHES.add(this);
    }

    public S getSubCache(Level level) {
        if (level instanceof ServerLevel server) {
            return server.getDataStorage().computeIfAbsent(tag -> {
                Data data = new Data(factory.apply(server));
                data.cache.load(tag);
                return data;
            }, () -> new Data(factory.apply(server)), savedDataName).cache;
        }
        return clientCaches.computeIfAbsent(level, factory);
    }

    /** Upstream {@code MarkerSavedData}, which also rewrote itself on every save. */
    private final class Data extends SavedData {
        final S cache;

        Data(S cache) {
            this.cache = cache;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            return cache.save(tag);
        }

        @Override
        public boolean isDirty() {
            return true;
        }
    }
}
