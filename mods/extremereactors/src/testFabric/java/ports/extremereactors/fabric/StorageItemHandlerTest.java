/*
 * Extreme Reactors 1.19.2 port - Fabric adapter tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Walking another mod's item storage slot by slot must not iterate the storage once per slot.
 */
class StorageItemHandlerTest {

    @Test
    void walkingTheSlotsIteratesTheStorageOnce() {

        final FakeStorage storage = new FakeStorage(100);
        final FabricTransfer.StorageItemHandler handler = new FabricTransfer.StorageItemHandler(storage);
        final int slots = handler.getSlots();

        assertEquals(100, slots);

        for (int slot = 0; slot < slots; ++slot) {
            assertEquals(slot + 1, handler.getSlotLimit(slot));
        }

        assertEquals(1, storage.iterations);
    }

    @Test
    void eachWalkSeesTheStorageAsItIsNow() {

        final FakeStorage storage = new FakeStorage(2);
        final FabricTransfer.StorageItemHandler handler = new FabricTransfer.StorageItemHandler(storage);

        assertEquals(2, handler.getSlots());
        storage.resize(3);
        assertEquals(3, handler.getSlots());
        assertEquals(3, handler.getSlotLimit(2));
        assertEquals(2, storage.iterations);
    }

    @Test
    void aSlotPastTheListTakesANewOne() {

        final FakeStorage storage = new FakeStorage(1);
        final FabricTransfer.StorageItemHandler handler = new FabricTransfer.StorageItemHandler(storage);

        assertEquals(1, handler.getSlots());
        storage.resize(2);
        assertEquals(2, handler.getSlotLimit(1));
        // a slot the storage does not have
        assertEquals(64, handler.getSlotLimit(5));
        assertEquals(64, handler.getSlotLimit(-1));
    }

    @Test
    void anEmptyStorageStillReportsOneSlot() {
        assertEquals(1, new FabricTransfer.StorageItemHandler(new FakeStorage(0)).getSlots());
    }

    /** A storage whose n-th view has a capacity of n + 1, counting how often it is iterated. */
    private static final class FakeStorage implements Storage<ItemVariant> {

        FakeStorage(final int size) {
            this.resize(size);
        }

        void resize(final int size) {

            this._views.clear();

            for (int i = 0; i < size; ++i) {
                this._views.add(new FakeView(i + 1));
            }
        }

        @Override
        public long insert(final ItemVariant resource, final long maxAmount, final TransactionContext transaction) {
            return 0;
        }

        @Override
        public long extract(final ItemVariant resource, final long maxAmount, final TransactionContext transaction) {
            return 0;
        }

        @Override
        public Iterator<StorageView<ItemVariant>> iterator() {

            ++this.iterations;
            return new ArrayList<>(this._views).iterator();
        }

        int iterations;
        private final List<StorageView<ItemVariant>> _views = new ArrayList<>();
    }

    private record FakeView(long capacity) implements StorageView<ItemVariant> {

        @Override
        public long extract(final ItemVariant resource, final long maxAmount, final TransactionContext transaction) {
            return 0;
        }

        @Override
        public boolean isResourceBlank() {
            return true;
        }

        @Override
        public ItemVariant getResource() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getAmount() {
            return 0;
        }

        @Override
        public long getCapacity() {
            return this.capacity;
        }
    }
}
