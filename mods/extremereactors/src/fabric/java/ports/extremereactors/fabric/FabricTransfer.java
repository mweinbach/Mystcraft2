/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.corelib.capability.Capability;
import ports.extremereactors.corelib.capability.CapabilityHelper;
import ports.extremereactors.corelib.capability.ICapabilityProvider;
import ports.extremereactors.corelib.capability.LazyOptional;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToLongFunction;

/**
 * Bridges the corelib item and fluid handlers with the Fabric Transfer API, in both directions.
 * <p>
 * The corelib handlers only offer simulate-then-execute operations, so the storages that expose them record what a
 * transaction moved and replay it on the handler when the outermost transaction commits. Fluid amounts are in
 * millibuckets on the corelib side and in droplets on the Fabric side; only whole millibuckets are moved.
 */
public final class FabricTransfer {

    public static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;

    /**
     * Another mod's handler, found at a block and already adapted to the corelib capability that stands for it.
     * Registered before any world loads.
     */
    @FunctionalInterface
    public interface ForeignLookup<T> {

        @Nullable T find(Level level, BlockPos position, BlockState state, @Nullable BlockEntity blockEntity,
                         Direction side);
    }

    /** Bridge an optional mod's block lookup. Call it before any world loads. */
    public static <T> void registerForeign(final Capability<T> capability, final ForeignLookup<T> lookup) {
        FOREIGN.add(new RegisteredForeign(capability, lookup));
    }

    /**
     * Expose the handlers of the block entities of these blocks. Registered per block rather than per block entity
     * type because a block may be listed by more than one type.
     */
    public static void registerBlockLookups(final Block... blocks) {

        ItemStorage.SIDED.registerForBlocks((level, position, state, blockEntity, side) ->
                null == blockEntity ? null : itemStorageOf(blockEntity, side), blocks);
        FluidStorage.SIDED.registerForBlocks((level, position, state, blockEntity, side) ->
                null == blockEntity ? null : fluidStorageOf(blockEntity, side), blocks);
        FabricEnergy.registerBlockLookups(blocks);
    }

    @SuppressWarnings("unchecked")
    public static <T> LazyOptional<T> foreignCapability(final Level level, final BlockPos position, final BlockState state,
                                                        @Nullable final BlockEntity blockEntity,
                                                        final Capability<T> capability, @Nullable final Direction side) {

        if (null == side) {
            return LazyOptional.empty();
        }

        if (CapabilityHelper.ITEM_HANDLER == capability) {

            final Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, position, state, blockEntity, side);

            return null == storage ? LazyOptional.empty() : (LazyOptional<T>) LazyOptional.of(() -> new StorageItemHandler(storage));
        }

        if (CapabilityHelper.FLUID_HANDLER == capability) {

            final Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, position, state, blockEntity, side);

            return null == storage ? LazyOptional.empty() : (LazyOptional<T>) LazyOptional.of(() -> new StorageFluidHandler(storage));
        }

        if (CapabilityHelper.ENERGY == capability) {
            return (LazyOptional<T>) FabricEnergy.foreignEnergy(level, position, state, blockEntity, side);
        }

        for (final RegisteredForeign foreign : FOREIGN) {
            if (foreign.capability() == capability) {
                return adoptForeign(foreign, level, position, state, blockEntity, side);
            }
        }

        return LazyOptional.empty();
    }

    public static Storage<FluidVariant> asStorage(final IFluidHandler handler) {
        return new FluidHandlerStorage(handler);
    }

    /**
     * Run an operation in a transaction that is committed only when {@code simulate} is false. Nests into the
     * transaction of the current thread, if there is one.
     */
    static long transact(final boolean simulate, final ToLongFunction<Transaction> operation) {

        final TransactionContext current = Transaction.getCurrentUnsafe();

        try (Transaction transaction = null == current ? Transaction.openOuter() : current.openNested()) {

            final long result = operation.applyAsLong(transaction);

            if (!simulate) {
                transaction.commit();
            }

            return result;
        }
    }

    static int clampToInt(final long value) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }

    //region our handlers as Fabric storages

    @Nullable
    private static Storage<ItemVariant> itemStorageOf(final BlockEntity blockEntity, @Nullable final Direction side) {
        return blockEntity instanceof ICapabilityProvider provider ?
                provider.getCapability(CapabilityHelper.ITEM_HANDLER, side).map(FabricTransfer::asStorage).orElse(null) : null;
    }

    @Nullable
    private static Storage<FluidVariant> fluidStorageOf(final BlockEntity blockEntity, @Nullable final Direction side) {
        return blockEntity instanceof ICapabilityProvider provider ?
                provider.getCapability(CapabilityHelper.FLUID_HANDLER, side).map(FabricTransfer::asStorage).orElse(null) : null;
    }

    private static Storage<ItemVariant> asStorage(final IItemHandler handler) {

        final List<HandlerSlotStorage> slots = new ArrayList<>(handler.getSlots());

        for (int i = 0; i < handler.getSlots(); ++i) {
            slots.add(new HandlerSlotStorage(handler, i));
        }

        return new CombinedStorage<>(slots);
    }

    /**
     * What a transaction moved into (positive) or out of (negative) a handler, not applied yet.
     */
    private record Pending<V>(@Nullable V resource, long amount) {

        static <V> Pending<V> none() {
            return new Pending<>(null, 0);
        }
    }

    private static final class HandlerSlotStorage
            extends SnapshotParticipant<Pending<ItemVariant>>
            implements SingleSlotStorage<ItemVariant> {

        HandlerSlotStorage(final IItemHandler handler, final int slot) {

            this._handler = handler;
            this._slot = slot;
            this._pending = Pending.none();
        }

        @Override
        public long insert(final ItemVariant resource, final long maxAmount, final TransactionContext transaction) {

            StoragePreconditions.notBlankNotNegative(resource, maxAmount);

            final long already = Math.max(0, this._pending.amount());

            if (this._pending.amount() < 0 || (already > 0 && !resource.equals(this._pending.resource()))) {
                return 0;
            }

            final int total = clampToInt(already + maxAmount);
            final ItemStack remainder = this._handler.insertItem(this._slot, resource.toStack(total), true);
            final long accepted = Math.min(maxAmount, total - remainder.getCount() - already);

            if (accepted <= 0) {
                return 0;
            }

            this.updateSnapshots(transaction);
            this._pending = new Pending<>(resource, already + accepted);
            return accepted;
        }

        @Override
        public long extract(final ItemVariant resource, final long maxAmount, final TransactionContext transaction) {

            StoragePreconditions.notBlankNotNegative(resource, maxAmount);

            if (this._pending.amount() > 0 || !resource.matches(this._handler.getStackInSlot(this._slot))) {
                return 0;
            }

            final long already = -this._pending.amount();
            final ItemStack extracted = this._handler.extractItem(this._slot, clampToInt(already + maxAmount), true);

            if (!resource.matches(extracted)) {
                return 0;
            }

            final long accepted = Math.min(maxAmount, extracted.getCount() - already);

            if (accepted <= 0) {
                return 0;
            }

            this.updateSnapshots(transaction);
            this._pending = new Pending<>(resource, -(already + accepted));
            return accepted;
        }

        @Override
        public boolean isResourceBlank() {
            return this.getResource().isBlank();
        }

        @Override
        public ItemVariant getResource() {

            final ItemStack stack = this._handler.getStackInSlot(this._slot);

            if (stack.isEmpty()) {
                return this._pending.amount() > 0 ? this._pending.resource() : ItemVariant.blank();
            }

            return stack.getCount() + this._pending.amount() > 0 ? ItemVariant.of(stack) : ItemVariant.blank();
        }

        @Override
        public long getAmount() {
            return Math.max(0, this._handler.getStackInSlot(this._slot).getCount() + this._pending.amount());
        }

        @Override
        public long getCapacity() {

            final ItemVariant resource = this.getResource();
            final int limit = this._handler.getSlotLimit(this._slot);

            return resource.isBlank() ? limit : Math.min(limit, resource.getItem().getMaxStackSize());
        }

        @Override
        protected Pending<ItemVariant> createSnapshot() {
            return this._pending;
        }

        @Override
        protected void readSnapshot(final Pending<ItemVariant> snapshot) {
            this._pending = snapshot;
        }

        @Override
        protected void onFinalCommit() {

            final Pending<ItemVariant> pending = this._pending;

            this._pending = Pending.none();

            if (pending.amount() > 0 && null != pending.resource()) {
                this._handler.insertItem(this._slot, pending.resource().toStack(clampToInt(pending.amount())), false);
            } else if (pending.amount() < 0) {
                this._handler.extractItem(this._slot, clampToInt(-pending.amount()), false);
            }
        }

        private final IItemHandler _handler;
        private final int _slot;
        private Pending<ItemVariant> _pending;
    }

    private static final class FluidHandlerStorage
            extends SnapshotParticipant<Pending<FluidVariant>>
            implements Storage<FluidVariant> {

        FluidHandlerStorage(final IFluidHandler handler) {

            this._handler = handler;
            this._pending = Pending.none();
        }

        @Override
        public long insert(final FluidVariant resource, final long maxAmount, final TransactionContext transaction) {

            StoragePreconditions.notBlankNotNegative(resource, maxAmount);

            final long already = Math.max(0, this._pending.amount());

            if (this._pending.amount() < 0 || (already > 0 && !resource.equals(this._pending.resource()))) {
                return 0;
            }

            final int total = clampToInt(already + maxAmount / DROPLETS_PER_MB);
            final long accepted = Math.min(maxAmount / DROPLETS_PER_MB,
                    this._handler.fill(toStack(resource, total), IFluidHandler.FluidAction.SIMULATE) - already);

            if (accepted <= 0) {
                return 0;
            }

            this.updateSnapshots(transaction);
            this._pending = new Pending<>(resource, already + accepted);
            return accepted * DROPLETS_PER_MB;
        }

        @Override
        public long extract(final FluidVariant resource, final long maxAmount, final TransactionContext transaction) {

            StoragePreconditions.notBlankNotNegative(resource, maxAmount);

            final long already = Math.max(0, -this._pending.amount());

            if (this._pending.amount() > 0 || (already > 0 && !resource.equals(this._pending.resource()))) {
                return 0;
            }

            final int total = clampToInt(already + maxAmount / DROPLETS_PER_MB);
            final FluidStack drained = this._handler.drain(toStack(resource, total), IFluidHandler.FluidAction.SIMULATE);

            if (drained.isEmpty() || !resource.isOf(drained.getFluid())) {
                return 0;
            }

            final long accepted = Math.min(maxAmount / DROPLETS_PER_MB, drained.getAmount() - already);

            if (accepted <= 0) {
                return 0;
            }

            this.updateSnapshots(transaction);
            this._pending = new Pending<>(resource, -(already + accepted));
            return accepted * DROPLETS_PER_MB;
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {

            final List<StorageView<FluidVariant>> views = new ArrayList<>(this._handler.getTanks());

            for (int i = 0; i < this._handler.getTanks(); ++i) {
                views.add(new TankView(this, i));
            }

            return views.iterator();
        }

        @Override
        protected Pending<FluidVariant> createSnapshot() {
            return this._pending;
        }

        @Override
        protected void readSnapshot(final Pending<FluidVariant> snapshot) {
            this._pending = snapshot;
        }

        @Override
        protected void onFinalCommit() {

            final Pending<FluidVariant> pending = this._pending;

            this._pending = Pending.none();

            if (null == pending.resource() || 0 == pending.amount()) {
                return;
            }

            if (pending.amount() > 0) {
                this._handler.fill(toStack(pending.resource(), clampToInt(pending.amount())), IFluidHandler.FluidAction.EXECUTE);
            } else {
                this._handler.drain(toStack(pending.resource(), clampToInt(-pending.amount())), IFluidHandler.FluidAction.EXECUTE);
            }
        }

        private final IFluidHandler _handler;
        private Pending<FluidVariant> _pending;
    }

    private record TankView(FluidHandlerStorage storage, int tank) implements StorageView<FluidVariant> {

        @Override
        public long extract(final FluidVariant resource, final long maxAmount, final TransactionContext transaction) {
            return resource.equals(this.getResource()) ? this.storage.extract(resource, maxAmount, transaction) : 0;
        }

        @Override
        public boolean isResourceBlank() {
            return this.storage._handler.getFluidInTank(this.tank).isEmpty();
        }

        @Override
        public FluidVariant getResource() {
            return toVariant(this.storage._handler.getFluidInTank(this.tank));
        }

        @Override
        public long getAmount() {
            return this.storage._handler.getFluidInTank(this.tank).getAmount() * DROPLETS_PER_MB;
        }

        @Override
        public long getCapacity() {
            return this.storage._handler.getTankCapacity(this.tank) * DROPLETS_PER_MB;
        }
    }

    //endregion
    //region Fabric storages as corelib handlers

    private record StorageItemHandler(Storage<ItemVariant> storage) implements IItemHandler {

        @Override
        public int getSlots() {

            int count = 0;

            for (final StorageView<ItemVariant> ignored : this.storage) {
                ++count;
            }

            return Math.max(1, count);
        }

        @Override
        public ItemStack getStackInSlot(final int slot) {

            final StorageView<ItemVariant> view = this.view(slot);

            return null == view || view.isResourceBlank() ? ItemStack.EMPTY :
                    view.getResource().toStack(clampToInt(view.getAmount()));
        }

        @Override
        public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {

            if (stack.isEmpty() || !this.storage.supportsInsertion()) {
                return stack;
            }

            final StorageView<ItemVariant> view = this.view(slot);
            final Storage<ItemVariant> target = view instanceof SingleSlotStorage<ItemVariant> single ? single : this.storage;
            final ItemVariant resource = ItemVariant.of(stack);
            final long inserted = transact(simulate, transaction -> target.insert(resource, stack.getCount(), transaction));

            if (inserted <= 0) {
                return stack;
            }

            return inserted >= stack.getCount() ? ItemStack.EMPTY : resource.toStack(stack.getCount() - (int) inserted);
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {

            final StorageView<ItemVariant> view = this.view(slot);

            if (amount <= 0 || null == view || view.isResourceBlank()) {
                return ItemStack.EMPTY;
            }

            final ItemVariant resource = view.getResource();
            final long extracted = transact(simulate, transaction -> view.extract(resource, amount, transaction));

            return extracted > 0 ? resource.toStack((int) extracted) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(final int slot) {

            final StorageView<ItemVariant> view = this.view(slot);

            return null == view ? 64 : clampToInt(view.getCapacity());
        }

        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return true;
        }

        @Nullable
        private StorageView<ItemVariant> view(final int slot) {

            int index = 0;

            for (final StorageView<ItemVariant> view : this.storage) {

                if (index++ == slot) {
                    return view;
                }
            }

            return null;
        }
    }

    private record StorageFluidHandler(Storage<FluidVariant> storage) implements IFluidHandler {

        @Override
        public int getTanks() {

            int count = 0;

            for (final StorageView<FluidVariant> ignored : this.storage) {
                ++count;
            }

            return count;
        }

        @Override
        public FluidStack getFluidInTank(final int tank) {

            final StorageView<FluidVariant> view = this.view(tank);

            return null == view || view.isResourceBlank() ? FluidStack.EMPTY :
                    toStack(view.getResource(), clampToInt(view.getAmount() / DROPLETS_PER_MB));
        }

        @Override
        public int getTankCapacity(final int tank) {

            final StorageView<FluidVariant> view = this.view(tank);

            return null == view ? 0 : clampToInt(view.getCapacity() / DROPLETS_PER_MB);
        }

        @Override
        public boolean isFluidValid(final int tank, final FluidStack stack) {
            return true;
        }

        @Override
        public int fill(final FluidStack resource, final FluidAction action) {

            if (resource.isEmpty() || !this.storage.supportsInsertion()) {
                return 0;
            }

            final FluidVariant variant = toVariant(resource);
            final long requested = resource.getAmount() * DROPLETS_PER_MB;

            return (int) (transact(action.simulate(), transaction -> {

                final long possible = this.storage.simulateInsert(variant, requested, transaction);
                final long aligned = possible - possible % DROPLETS_PER_MB;

                return aligned > 0 ? this.storage.insert(variant, aligned, transaction) : 0;

            }) / DROPLETS_PER_MB);
        }

        @Override
        public FluidStack drain(final FluidStack resource, final FluidAction action) {

            if (resource.isEmpty() || !this.storage.supportsExtraction()) {
                return FluidStack.EMPTY;
            }

            return this.drain(toVariant(resource), resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(final int maxDrain, final FluidAction action) {

            if (maxDrain <= 0 || !this.storage.supportsExtraction()) {
                return FluidStack.EMPTY;
            }

            final FluidVariant variant = StorageUtil.findExtractableResource(this.storage, null);

            return null == variant ? FluidStack.EMPTY : this.drain(variant, maxDrain, action);
        }

        private FluidStack drain(final FluidVariant variant, final int amount, final FluidAction action) {

            final long requested = amount * DROPLETS_PER_MB;
            final long drained = transact(action.simulate(), transaction -> {

                final long possible = this.storage.simulateExtract(variant, requested, transaction);
                final long aligned = possible - possible % DROPLETS_PER_MB;

                return aligned > 0 ? this.storage.extract(variant, aligned, transaction) : 0;
            });

            return drained > 0 ? toStack(variant, (int) (drained / DROPLETS_PER_MB)) : FluidStack.EMPTY;
        }

        @Nullable
        private StorageView<FluidVariant> view(final int tank) {

            int index = 0;

            for (final StorageView<FluidVariant> view : this.storage) {

                if (index++ == tank) {
                    return view;
                }
            }

            return null;
        }
    }

    //endregion
    //region conversions

    static FluidStack toStack(final FluidVariant variant, final int amount) {
        return variant.isBlank() || amount <= 0 ? FluidStack.EMPTY : new FluidStack(variant.getFluid(), amount, variant.copyNbt());
    }

    static FluidVariant toVariant(final FluidStack stack) {
        return stack.isEmpty() ? FluidVariant.blank() : FluidVariant.of(stack.getFluid(), stack.hasTag() ? stack.getTag() : null);
    }

    //endregion

    @SuppressWarnings("unchecked")
    private static <T> LazyOptional<T> adoptForeign(final RegisteredForeign foreign, final Level level,
                                                    final BlockPos position, final BlockState state,
                                                    @Nullable final BlockEntity blockEntity, final Direction side) {

        final Object found = ((ForeignLookup<Object>) foreign.lookup()).find(level, position, state, blockEntity, side);

        return null == found ? LazyOptional.empty() : (LazyOptional<T>) LazyOptional.of(() -> found);
    }

    private record RegisteredForeign(Capability<?> capability, ForeignLookup<?> lookup) {
    }

    private static final List<RegisteredForeign> FOREIGN = new CopyOnWriteArrayList<>();

    private FabricTransfer() {
    }
}
