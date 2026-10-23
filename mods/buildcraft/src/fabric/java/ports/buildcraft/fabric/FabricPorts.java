package ports.buildcraft.fabric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.InventoryUtil;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.platform.PortHolder;
import team.reborn.energy.api.EnergyStorage;

/**
 * Bridges shared ports and the Fabric Transfer API / Team Reborn Energy in both directions.
 *
 * <p>Ports with a snapshot execute immediately and are rolled back when a transaction aborts. Ports
 * without one (pipes accepting items and fluids, wooden pipes accepting power) only simulate inside
 * a transaction; the committed amount runs at the end of the server tick, because Fabric forbids
 * opening transactions from close callbacks and those ports reach into neighbouring storages.
 * BuildCraft blocks talking to each other bypass this bridge and call the shared ports directly.
 */
final class FabricPorts {
    static final long DROPLETS_PER_MB = 81;
    private static final Map<BlockEntity, Adapter[]> ADAPTERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Adapter> QUEUED = new LinkedHashSet<>();

    private FabricPorts() {}

    static void register(BlockEntityType<?>... types) {
        EnergyStorage.SIDED.registerForBlockEntities((tile, side) -> {
            EnergyPort port = tile instanceof PortHolder holder ? holder.energyPort(side) : null;
            return port == null ? null : (EnergyStorage) adapter(tile, 0, side, () -> new EnergyAdapter(tile, side, port));
        }, types);
        FluidStorage.SIDED.registerForBlockEntities((tile, side) -> {
            FluidPort port = tile instanceof PortHolder holder ? holder.fluidPort(side) : null;
            return port == null ? null : (FluidAdapter) adapter(tile, 1, side, () -> new FluidAdapter(tile, side, port));
        }, types);
        ItemStorage.SIDED.registerForBlockEntities((tile, side) -> {
            ItemPort port = tile instanceof PortHolder holder ? holder.itemPort(side) : null;
            if (port != null) return (ItemAdapter) adapter(tile, 2, side, () -> new ItemAdapter(tile, side, port));
            return tile instanceof Container container ? InventoryStorage.of(container, side) : null;
        }, types);
        ServerTickEvents.END_SERVER_TICK.register(server -> runQueued());
    }

    private interface AdapterFactory {
        Adapter create();
    }

    private static Adapter adapter(BlockEntity tile, int kind, @Nullable Direction side, AdapterFactory factory) {
        int index = kind * 7 + (side == null ? 6 : side.get3DDataValue());
        synchronized (ADAPTERS) {
            Adapter[] adapters = ADAPTERS.computeIfAbsent(tile, key -> new Adapter[21]);
            if (adapters[index] == null) adapters[index] = factory.create();
            return adapters[index];
        }
    }

    /**
     * Forgets the adapters made for {@code tile}, so the next lookup wraps its current ports. Fabric's
     * block API caches query the provider on every find; adapters callers kept refuse to move anything
     * once their face stops exposing that kind of port.
     */
    static void invalidate(BlockEntity tile) {
        ADAPTERS.remove(tile);
    }

    static void runQueued() {
        if (QUEUED.isEmpty()) return;
        List<Adapter> adapters = new ArrayList<>(QUEUED);
        QUEUED.clear();
        for (Adapter adapter : adapters) adapter.runQueued();
    }

    // Shared ports exposed as Fabric storages.

    private abstract static class Adapter extends SnapshotParticipant<Object> {
        final BlockEntity tile;
        @Nullable final Direction side;
        final boolean rollback;

        Adapter(BlockEntity tile, @Nullable Direction side, boolean rollback) {
            this.tile = tile;
            this.side = side;
            this.rollback = rollback;
        }

        final boolean live() {
            return !tile.isRemoved() && tile instanceof PortHolder holder && exposed(holder);
        }

        abstract boolean exposed(PortHolder holder);

        @Override
        protected final void onFinalCommit() {
            if (rollback) {
                committed();
            } else if (queuePending()) {
                QUEUED.add(this);
            }
        }

        abstract void committed();

        /** Moves the amount accepted in the committed transaction to the end-of-tick queue. */
        abstract boolean queuePending();

        abstract void runQueued();
    }

    private static final class EnergyAdapter extends Adapter implements EnergyStorage {
        private final EnergyPort port;
        private long pending;
        private long queued;

        EnergyAdapter(BlockEntity tile, @Nullable Direction side, EnergyPort port) {
            super(tile, side, port.snapshot() != null);
            this.port = port;
        }

        @Override boolean exposed(PortHolder holder) { return holder.energyPort(side) != null; }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            if (maxAmount <= 0 || !live()) return 0;
            if (rollback) {
                updateSnapshots(transaction);
                return port.receive(maxAmount, false);
            }
            long already = pending + queued;
            long accepted = Math.max(0, port.receive(already + maxAmount, true) - already);
            if (accepted > 0) {
                updateSnapshots(transaction);
                pending += accepted;
            }
            return accepted;
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            if (!rollback || maxAmount <= 0 || !live()) return 0;
            updateSnapshots(transaction);
            return port.extract(maxAmount, false);
        }

        @Override public long getAmount() { return port.stored(); }
        @Override public long getCapacity() { return port.capacity(); }
        @Override public boolean supportsInsertion() { return port.canReceive() && live(); }
        @Override public boolean supportsExtraction() { return rollback && port.canExtract() && live(); }
        @Override protected Object createSnapshot() { return rollback ? port.snapshot() : pending; }

        @Override
        protected void readSnapshot(Object snapshot) {
            if (rollback) port.restore(snapshot);
            else pending = (Long) snapshot;
        }

        @Override void committed() { port.onCommitted(); }

        @Override
        boolean queuePending() {
            queued += pending;
            pending = 0;
            return queued > 0;
        }

        @Override
        void runQueued() {
            long amount = queued;
            queued = 0;
            if (!tile.isRemoved()) port.receive(amount, false);
        }
    }

    private static final class FluidAdapter extends Adapter implements Storage<FluidVariant> {
        private final FluidPort port;
        private FluidAmount pending = FluidAmount.EMPTY;
        private FluidAmount queued = FluidAmount.EMPTY;

        FluidAdapter(BlockEntity tile, @Nullable Direction side, FluidPort port) {
            super(tile, side, port.snapshot() != null);
            this.port = port;
        }

        @Override boolean exposed(PortHolder holder) { return holder.fluidPort(side) != null; }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            long mb = maxAmount / DROPLETS_PER_MB;
            if (resource.isBlank() || resource.hasNbt() || mb <= 0 || !live()) return 0;
            Fluid fluid = resource.getFluid();
            if (rollback) {
                updateSnapshots(transaction);
                return port.fill(fluid, mb, false) * DROPLETS_PER_MB;
            }
            FluidAmount already = merge(pending, queued);
            if (already == null || (!already.isEmpty() && already.fluid() != fluid)) return 0;
            long accepted = Math.max(0, port.fill(fluid, already.amount() + mb, true) - already.amount());
            if (accepted > 0) {
                updateSnapshots(transaction);
                pending = new FluidAmount(fluid, pending.amount() + accepted);
            }
            return accepted * DROPLETS_PER_MB;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            long mb = maxAmount / DROPLETS_PER_MB;
            if (!rollback || resource.isBlank() || resource.hasNbt() || mb <= 0 || !live()) return 0;
            updateSnapshots(transaction);
            return port.drain(resource.getFluid(), mb, false).amount() * DROPLETS_PER_MB;
        }

        @Override public boolean supportsInsertion() { return live(); }
        @Override public boolean supportsExtraction() { return rollback && live(); }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            if (!live()) return Collections.emptyIterator();
            List<FluidAmount> contents = port.contents();
            List<StorageView<FluidVariant>> views = new ArrayList<>(contents.size());
            for (int i = 0; i < contents.size(); i++) views.add(new FluidView(this, contents.get(i), port.capacity(i)));
            return views.iterator();
        }

        @Override protected Object createSnapshot() { return rollback ? port.snapshot() : pending; }

        @Override
        protected void readSnapshot(Object snapshot) {
            if (rollback) port.restore(snapshot);
            else pending = (FluidAmount) snapshot;
        }

        @Override void committed() { port.onCommitted(); }

        @Override
        boolean queuePending() {
            FluidAmount merged = merge(queued, pending);
            if (merged != null) queued = merged;
            pending = FluidAmount.EMPTY;
            return !queued.isEmpty();
        }

        @Override
        void runQueued() {
            FluidAmount amount = queued;
            queued = FluidAmount.EMPTY;
            if (!tile.isRemoved()) port.fill(amount.fluid(), amount.amount(), false);
        }

        @Nullable
        private static FluidAmount merge(FluidAmount a, FluidAmount b) {
            if (a.isEmpty()) return b;
            if (b.isEmpty()) return a;
            return a.fluid() == b.fluid() ? a.withAmount(a.amount() + b.amount()) : null;
        }
    }

    private record FluidView(FluidAdapter storage, FluidAmount amount, long capacity) implements StorageView<FluidVariant> {
        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            return resource.isOf(amount.fluid()) ? storage.extract(resource, maxAmount, transaction) : 0;
        }

        @Override public boolean isResourceBlank() { return amount.isEmpty(); }
        @Override public FluidVariant getResource() { return amount.isEmpty() ? FluidVariant.blank() : FluidVariant.of(amount.fluid()); }
        @Override public long getAmount() { return amount.amount() * DROPLETS_PER_MB; }
        @Override public long getCapacity() { return capacity * DROPLETS_PER_MB; }
    }

    /** Pipes and providing machines accept items and expose no stored contents. */
    private static final class ItemAdapter extends Adapter implements Storage<ItemVariant> {
        private final ItemPort port;
        private final List<ItemStack> pending = new ArrayList<>();
        private final List<ItemStack> queued = new ArrayList<>();

        ItemAdapter(BlockEntity tile, @Nullable Direction side, ItemPort port) {
            super(tile, side, port.snapshot() != null);
            this.port = port;
        }

        @Override boolean exposed(PortHolder holder) { return holder.itemPort(side) != null; }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            if (resource.isBlank() || maxAmount <= 0 || !live()) return 0;
            int count = (int) Math.min(maxAmount, resource.getItem().getMaxStackSize());
            ItemStack stack = resource.toStack(count);
            if (rollback) {
                updateSnapshots(transaction);
                return count - port.insert(stack, false).getCount();
            }
            int accepted = count - port.insert(stack, true).getCount();
            if (accepted > 0) {
                updateSnapshots(transaction);
                pending.add(resource.toStack(accepted));
            }
            return accepted;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            if (!rollback || resource.isBlank() || maxAmount <= 0 || !live()) return 0;
            updateSnapshots(transaction);
            int max = (int) Math.min(maxAmount, resource.getItem().getMaxStackSize());
            return port.extract(resource::matches, max, false).getCount();
        }

        /** Providing machines (the mining well and the quarry) expose a storage so pipes connect, but take nothing. */
        @Override public boolean supportsInsertion() { return port != InventoryUtil.PROVIDING && live(); }
        @Override public boolean supportsExtraction() { return rollback && live(); }
        @Override public Iterator<StorageView<ItemVariant>> iterator() { return Collections.emptyIterator(); }
        @Override protected Object createSnapshot() { return rollback ? port.snapshot() : pending.size(); }

        @Override
        protected void readSnapshot(Object snapshot) {
            if (rollback) {
                port.restore(snapshot);
                return;
            }
            int size = (Integer) snapshot;
            while (pending.size() > size) pending.remove(pending.size() - 1);
        }

        @Override void committed() { port.onCommitted(); }

        @Override
        boolean queuePending() {
            queued.addAll(pending);
            pending.clear();
            return !queued.isEmpty();
        }

        @Override
        void runQueued() {
            List<ItemStack> stacks = new ArrayList<>(queued);
            queued.clear();
            Level level = tile.getLevel();
            for (ItemStack stack : stacks) {
                ItemStack left = tile.isRemoved() ? stack : port.insert(stack, false);
                if (!left.isEmpty() && level != null) Block.popResource(level, tile.getBlockPos(), left);
            }
        }
    }

    // Fabric storages of other blocks wrapped as shared ports.

    private static Transaction open() {
        return Transaction.openNested(Transaction.getCurrentUnsafe());
    }

    @Nullable
    static EnergyPort findEnergy(Level level, BlockPos pos, Direction side) {
        if (level.getBlockEntity(pos) instanceof PortHolder holder) return holder.energyPort(side);
        EnergyStorage storage = EnergyStorage.SIDED.find(level, pos, side);
        if (storage == null) return null;
        return new EnergyPort() {
            @Override
            public long receive(long amount, boolean simulate) {
                try (Transaction transaction = open()) {
                    long accepted = storage.insert(amount, transaction);
                    if (!simulate) transaction.commit();
                    return accepted;
                }
            }

            @Override
            public long extract(long amount, boolean simulate) {
                try (Transaction transaction = open()) {
                    long extracted = storage.extract(amount, transaction);
                    if (!simulate) transaction.commit();
                    return extracted;
                }
            }

            @Override public long stored() { return storage.getAmount(); }
            @Override public long capacity() { return storage.getCapacity(); }
            @Override public boolean canReceive() { return storage.supportsInsertion(); }
            @Override public boolean canExtract() { return storage.supportsExtraction(); }
        };
    }

    @Nullable
    static ItemPort findItems(Level level, BlockPos pos, Direction side) {
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile instanceof PortHolder holder) {
            ItemPort port = holder.itemPort(side);
            if (port != null || !(tile instanceof Container)) return port;
        }
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, side);
        if (storage == null && tile instanceof WorldlyContainer sided) storage = InventoryStorage.of(sided, side);
        if (storage == null) return null;
        Storage<ItemVariant> items = storage;
        return new ItemPort() {
            @Override
            public ItemStack insert(ItemStack stack, boolean simulate) {
                if (stack.isEmpty()) return stack;
                try (Transaction transaction = open()) {
                    long inserted = items.insert(ItemVariant.of(stack), stack.getCount(), transaction);
                    if (!simulate) transaction.commit();
                    ItemStack left = stack.copy();
                    left.shrink((int) inserted);
                    return left;
                }
            }

            @Override
            public ItemStack extract(Predicate<ItemStack> filter, int max, boolean simulate) {
                try (Transaction transaction = open()) {
                    for (StorageView<ItemVariant> view : items) {
                        if (view.isResourceBlank() || view.getAmount() <= 0) continue;
                        ItemVariant resource = view.getResource();
                        if (!filter.test(resource.toStack())) continue;
                        long extracted = items.extract(resource, Math.min(max, resource.getItem().getMaxStackSize()), transaction);
                        if (extracted <= 0) continue;
                        if (!simulate) transaction.commit();
                        return resource.toStack((int) extracted);
                    }
                }
                return ItemStack.EMPTY;
            }

            @Override
            public List<SlotView> slots() {
                List<SlotView> slots = new ArrayList<>();
                for (StorageView<ItemVariant> view : items) {
                    ItemStack stack = view.isResourceBlank() ? ItemStack.EMPTY : view.getResource().toStack((int) Math.min(Integer.MAX_VALUE, view.getAmount()));
                    int limit = (int) Math.min(Integer.MAX_VALUE, view.getCapacity());
                    Storage<ItemVariant> target = view instanceof SingleSlotStorage<ItemVariant> slot ? slot : items;
                    slots.add(new StorageSlot(target, stack, limit));
                }
                return slots;
            }
        };
    }

    /** Views that are not single slots fall back to a simulated insert into the whole storage. */
    private record StorageSlot(Storage<ItemVariant> target, ItemStack stack, int limit) implements ItemPort.SlotView {
        @Override
        public ItemStack simulateInsert(ItemStack insert) {
            if (insert.isEmpty()) return insert;
            try (Transaction transaction = open()) {
                long inserted = target.insert(ItemVariant.of(insert), insert.getCount(), transaction);
                ItemStack left = insert.copy();
                left.shrink((int) inserted);
                return left;
            }
        }
    }

    @Nullable
    static FluidPort findFluids(Level level, BlockPos pos, Direction side) {
        if (level.getBlockEntity(pos) instanceof PortHolder holder) return holder.fluidPort(side);
        Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, side);
        return storage == null ? null : new ForeignFluids(storage);
    }

    /** Converts millibuckets to droplets, moving only whole millibuckets so both loaders agree. */
    private record ForeignFluids(Storage<FluidVariant> storage) implements FluidPort {
        @Override
        public long fill(Fluid fluid, long amount, boolean simulate) {
            FluidVariant variant = FluidVariant.of(fluid);
            long accepted;
            try (Transaction transaction = open()) {
                accepted = storage.insert(variant, amount * DROPLETS_PER_MB, transaction) / DROPLETS_PER_MB;
            }
            if (simulate || accepted <= 0) return accepted;
            try (Transaction transaction = open()) {
                if (storage.insert(variant, accepted * DROPLETS_PER_MB, transaction) != accepted * DROPLETS_PER_MB) return 0;
                transaction.commit();
                return accepted;
            }
        }

        @Override
        public FluidAmount drain(@Nullable Fluid filter, long amount, boolean simulate) {
            FluidVariant variant = filter == null ? firstExtractable() : FluidVariant.of(filter);
            if (variant == null || variant.isBlank()) return FluidAmount.EMPTY;
            long drained;
            try (Transaction transaction = open()) {
                drained = storage.extract(variant, amount * DROPLETS_PER_MB, transaction) / DROPLETS_PER_MB;
            }
            if (simulate || drained <= 0) return new FluidAmount(variant.getFluid(), drained);
            try (Transaction transaction = open()) {
                if (storage.extract(variant, drained * DROPLETS_PER_MB, transaction) != drained * DROPLETS_PER_MB) return FluidAmount.EMPTY;
                transaction.commit();
                return new FluidAmount(variant.getFluid(), drained);
            }
        }

        @Nullable
        private FluidVariant firstExtractable() {
            for (StorageView<FluidVariant> view : storage) {
                if (!view.isResourceBlank() && !view.getResource().hasNbt() && view.getAmount() >= DROPLETS_PER_MB) return view.getResource();
            }
            return null;
        }

        @Override
        public List<FluidAmount> contents() {
            List<FluidAmount> contents = new ArrayList<>();
            for (StorageView<FluidVariant> view : storage) {
                contents.add(view.isResourceBlank() ? FluidAmount.EMPTY : new FluidAmount(view.getResource().getFluid(), view.getAmount() / DROPLETS_PER_MB));
            }
            return contents;
        }

        @Override
        public long capacity(int index) {
            int i = 0;
            for (StorageView<FluidVariant> view : storage) {
                if (i++ == index) return view.getCapacity() / DROPLETS_PER_MB;
            }
            return 0;
        }
    }
}
