package ports.jurassicraft.vehicle;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiPredicate;
import net.minecraft.world.item.ItemStack;

/** Hold early record packets until the client has received the entity spawn. */
public final class RadioPendingQueue<W> {
    private static final int LIFETIME = 40;
    private static final class Pending<W> {
        final W world;
        final ItemStack record;
        int age;
        Pending(W world, ItemStack record) { this.world = world; this.record = record.copy(); }
    }
    private final Map<Integer, Pending<W>> pending = new HashMap<>();
    public void offer(int entityId, W world, ItemStack record) {
        pending.put(entityId, new Pending<>(world, record));
    }
    public void remove(int entityId) { pending.remove(entityId); }
    public int size() { return pending.size(); }
    public void tick(W world, BiPredicate<Integer, ItemStack> deliver) {
        Iterator<Map.Entry<Integer, Pending<W>>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Pending<W>> entry = iterator.next();
            Pending<W> value = entry.getValue();
            if (world == null || world != value.world || ++value.age > LIFETIME
                    || deliver.test(entry.getKey(), value.record)) iterator.remove();
        }
    }
}
