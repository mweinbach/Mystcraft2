/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.stack;

/**
 * A fixed number of slots holding stacks, which reports its changes to an optional listener.
 *
 * @param <Holder> the implementing type, returned by the fluent setters
 * @param <Stack> the stack type
 */
public interface IStackHolder<Holder extends IStackHolder<Holder, Stack>, Stack> {

    enum ChangeType {

        /** A stack was put in an empty slot. */
        Added,
        /** A slot was emptied. */
        Removed,
        /** A stack was replaced by a different one. */
        Replaced,
        /** Only the amount of a stack changed. */
        Modified,
        /** The whole holder was loaded from saved data. */
        Loaded;

        /**
         * True if listeners must re-evaluate the slot contents. Every change except a bulk load qualifies; load
         * listeners are notified separately.
         */
        public boolean fullChange() {
            return this != Loaded;
        }
    }

    @FunctionalInterface
    interface IChangeListener {
        void onChange(ChangeType changeType, int index);
    }

    Holder setOnLoadListener(Runnable listener);

    Holder setOnContentsChangedListener(IChangeListener listener);

    int getSlots();

    /** The stack in the slot; callers must not modify it. */
    Stack getStackAt(int index);

    int getAmount(int index);

    boolean isEmpty(int index);

    default boolean isEmpty() {

        for (int index = 0; index < this.getSlots(); ++index) {
            if (!this.isEmpty(index)) {
                return false;
            }
        }

        return true;
    }
}
