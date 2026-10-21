/*
 * Extreme Reactors 1.19.2 port - visual effects
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.effects;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Client-side state per machine, keyed by identity. The state refers back to its machine and that machine's
 * parts, so entries are held strongly and only freed by removing them: {@link #sweep} drops every machine that
 * has stopped ticking, which covers disassembly, chunk unloads and leaving the world.
 */
public final class MachineStateCache<K, V> {

    /** A machine that has not ticked for more than this many game ticks is dropped. */
    public static final int MAX_IDLE_TICKS = 2;

    /**
     * Record that the machine ticked, creating its state on the first tick.
     *
     * @return the machine's state
     */
    public V tick(final K machine, final long gameTime, final Function<K, V> factory) {

        final Entry<V> entry = this._entries.computeIfAbsent(machine, key -> new Entry<>(factory.apply(key)));

        entry.lastTick = gameTime;
        return entry.state;
    }

    @Nullable
    public V get(final K machine) {

        final Entry<V> entry = this._entries.get(machine);

        return null == entry ? null : entry.state;
    }

    @Nullable
    public V remove(final K machine) {

        final Entry<V> entry = this._entries.remove(machine);

        return null == entry ? null : entry.state;
    }

    /**
     * Drop the machines that have not ticked lately. A last tick after {@code gameTime} means the game time was
     * reset, as when joining another world, so those go too.
     */
    public void sweep(final long gameTime, final Consumer<V> onRemoved) {
        this.removeIf((machine, entry) -> gameTime - entry.lastTick > MAX_IDLE_TICKS || entry.lastTick > gameTime,
                onRemoved);
    }

    public void removeIf(final Predicate<K> machines, final Consumer<V> onRemoved) {
        this.removeIf((machine, entry) -> machines.test(machine), onRemoved);
    }

    public int size() {
        return this._entries.size();
    }

    //region internals

    private static final class Entry<V> {

        Entry(final V state) {
            this.state = state;
        }

        final V state;
        long lastTick;
    }

    private interface EntryPredicate<K, V> {
        boolean test(K machine, Entry<V> entry);
    }

    private void removeIf(final EntryPredicate<K, V> predicate, final Consumer<V> onRemoved) {

        for (final Iterator<Map.Entry<K, Entry<V>>> it = this._entries.entrySet().iterator(); it.hasNext(); ) {

            final Map.Entry<K, Entry<V>> mapEntry = it.next();
            final Entry<V> entry = mapEntry.getValue();

            if (predicate.test(mapEntry.getKey(), entry)) {

                it.remove();
                onRemoved.accept(entry.state);
            }
        }
    }

    private final Map<K, Entry<V>> _entries = new IdentityHashMap<>();

    //endregion
}
