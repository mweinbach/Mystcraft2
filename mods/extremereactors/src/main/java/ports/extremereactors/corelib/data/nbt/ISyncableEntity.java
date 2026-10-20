/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.nbt;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.nbt.CompoundTag;

/** Something that saves its state to, and restores it from, NBT; for disk saves and client updates. */
public interface ISyncableEntity {

    enum SyncReason {

        /** Saving to, or loading from, disk. */
        FullSync,
        /** A partial update sent over the network. */
        NetworkUpdate;

        public boolean isFullSync() {
            return this == FullSync;
        }

        public boolean isNetworkUpdate() {
            return this == NetworkUpdate;
        }
    }

    void syncDataFrom(CompoundTag data, SyncReason syncReason);

    CompoundTag syncDataTo(CompoundTag data, SyncReason syncReason);

    default void syncChildDataEntityFrom(final ISyncableEntity child, final String key, final CompoundTag data,
                                         final SyncReason syncReason) {

        if (data.contains(key)) {
            child.syncDataFrom(data.getCompound(key), syncReason);
        }
    }

    default void syncChildDataEntityTo(final ISyncableEntity child, final String key, final CompoundTag data,
                                       final SyncReason syncReason) {
        data.put(key, child.syncDataTo(new CompoundTag(), syncReason));
    }

    default void syncDataElementFrom(final String key, final CompoundTag data, final ICompoundSerializable element) {

        if (data.contains(key)) {
            element.deserializeNBT(data.getCompound(key));
        }
    }

    default void syncDataElementTo(final String key, final CompoundTag data, final ICompoundSerializable element) {
        data.put(key, element.serializeNBT());
    }

    default void syncBooleanElementFrom(final String key, final CompoundTag data, final BooleanConsumer consumer) {

        if (data.contains(key)) {
            consumer.accept(data.getBoolean(key));
        }
    }

    default void syncBooleanElementTo(final String key, final CompoundTag data, final boolean value) {
        data.putBoolean(key, value);
    }
}
