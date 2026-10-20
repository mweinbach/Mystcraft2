package ports.buildcraft.lib;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Upstream's {@code IWorldEventListener.notifyBlockUpdate} for server levels: listeners hear every block
 * change that vanilla sends to clients, fed by {@code ServerLevelBlockUpdateMixin}.
 */
public final class BlockUpdateListeners {
    @FunctionalInterface
    public interface Listener {
        void onBlockUpdate(BlockPos pos);
    }

    private static final Map<ServerLevel, Set<Listener>> LISTENERS = new WeakHashMap<>();

    private BlockUpdateListeners() {}

    public static synchronized void add(ServerLevel level, Listener listener) {
        LISTENERS.computeIfAbsent(level, l -> Collections.newSetFromMap(new IdentityHashMap<>())).add(listener);
    }

    public static synchronized void remove(ServerLevel level, Listener listener) {
        Set<Listener> listeners = LISTENERS.get(level);
        if (listeners != null) listeners.remove(listener);
    }

    public static void fire(ServerLevel level, BlockPos pos) {
        List<Listener> listeners;
        synchronized (BlockUpdateListeners.class) {
            Set<Listener> set = LISTENERS.get(level);
            if (set == null || set.isEmpty()) return;
            listeners = List.copyOf(set);
        }
        for (Listener listener : listeners) listener.onBlockUpdate(pos);
    }
}
