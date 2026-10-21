/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
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
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.common.capabilities.Capability;
import ports.mekanism.lib.common.capabilities.ForgeCapabilities;
import ports.mekanism.lib.common.util.LazyOptional;
import ports.mekanism.lib.fluids.FluidStack;
import ports.mekanism.lib.fluids.capability.IFluidHandler;
import ports.mekanism.lib.fluids.capability.IFluidHandlerItem;
import ports.mekanism.lib.items.IItemHandler;

/**
 * Bridges Forge's item and fluid handlers with the Fabric Transfer API, in both directions.
 * <p>
 * Forge handlers only simulate or execute, so the storages exposing them record what a transaction moved and apply it
 * when the outermost transaction commits. Forge measures fluids in millibuckets and Fabric in droplets; only whole
 * millibuckets move.
 */
public final class FabricTransfer {

    static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;

    private static Set<String> ownNamespaces = Set.of();
    private static Set<BlockEntityType<?>> exported = Set.of();

    private FabricTransfer() {
    }

    /**
     * Exposes the handlers, and the Forge Energy storages as Team Reborn Energy, of the block entities the port's mods
     * register.
     */
    static void registerBlockEntityLookups(Set<String> namespaces) {
        ownNamespaces = Set.copyOf(namespaces);
        BlockEntityType<?>[] types = Registry.BLOCK_ENTITY_TYPE.entrySet().stream()
              .filter(entry -> namespaces.contains(entry.getKey().location().getNamespace()))
              .map(Map.Entry::getValue)
              .toArray(BlockEntityType<?>[]::new);
        exported = Set.of(types);
        ItemStorage.SIDED.registerForBlockEntities((blockEntity, side) -> blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
              .<Storage<ItemVariant>>map(FabricTransfer::asStorage).orElse(null), types);
        FluidStorage.SIDED.registerForBlockEntities((blockEntity, side) -> blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side)
              .<Storage<FluidVariant>>map(HandlerFluidStorage::new).orElse(null), types);
        FabricEnergy.registerBlockEntityLookups(types);
        FabricEnergy.registerItemLookups(ownNamespaces);
        registerItemFluidLookups(ownNamespaces);
    }

    /**
     * Exposes the port's own fluid items. Other mods' items, including vanilla buckets, keep the providers registered
     * ahead of this fallback. Looking those up here would call back into {@link #foreignCapability(ItemStack, Capability)}.
     */
    private static void registerItemFluidLookups(Set<String> namespaces) {
        Set<String> mods = Set.copyOf(namespaces);
        FluidStorage.ITEM.registerFallback((stack, context) -> owned(mods, stack) && fluidHandler(stack) != null
              ? new ItemFluidStorage(context) : null);
    }

    private static boolean owned(Set<String> namespaces, ItemStack stack) {
        return !stack.isEmpty() && namespaces.contains(Registry.ITEM.getKey(stack.getItem()).getNamespace());
    }

    @Nullable
    private static IFluidHandler fluidHandler(ItemStack stack) {
        IFluidHandler item = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        return item != null ? item : stack.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
    }

    /**
     * Puts a stack a Forge item handler has already updated into the context for this transaction.
     */
    static boolean commitItem(ContainerItemContext context, ItemStack updated, TransactionContext transaction) {
        if (updated.isEmpty()) {
            return context.getItemVariant().isBlank()
                  || context.extract(context.getItemVariant(), context.getAmount(), transaction) == context.getAmount();
        }
        return context.exchange(ItemVariant.of(updated), updated.getCount(), transaction) == updated.getCount();
    }

    /**
     * The item, fluid or energy storage another mod's block entity offers through the Fabric APIs. The port's own block
     * entities answer through their Forge capabilities, so they are never looked up here.
     */
    @SuppressWarnings("unchecked")
    static <T> LazyOptional<T> foreignCapability(BlockEntity blockEntity, Capability<T> capability, @Nullable Direction side) {
        if (side == null || blockEntity.getLevel() == null || exported.contains(blockEntity.getType())) {
            return LazyOptional.empty();
        }
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            Storage<ItemVariant> storage = ItemStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
            return storage == null ? LazyOptional.empty() : (LazyOptional<T>) LazyOptional.of(() -> new StorageItemHandler(storage));
        } else if (capability == ForgeCapabilities.FLUID_HANDLER) {
            Storage<FluidVariant> storage = FluidStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
            return storage == null ? LazyOptional.empty() : (LazyOptional<T>) LazyOptional.of(() -> new StorageFluidHandler(storage));
        } else if (capability == ForgeCapabilities.ENERGY) {
            return (LazyOptional<T>) FabricEnergy.foreignEnergy(blockEntity, side);
        }
        return LazyOptional.empty();
    }

    /**
     * The fluid or energy storage another mod's item, or a vanilla bucket, offers through the Fabric APIs. The port's own
     * items are exposed through {@code EnergyStorage.ITEM} and {@code FluidStorage.ITEM} instead, so they are never
     * looked up here. Changes to the stack happen in place, as with Forge's item capabilities;
     * {@link IFluidHandlerItem#getContainer} holds the replacement when the item itself changes, such as a bucket being emptied.
     */
    @SuppressWarnings("unchecked")
    static <T> LazyOptional<T> foreignCapability(ItemStack stack, Capability<T> capability) {
        if (ownNamespaces.contains(Registry.ITEM.getKey(stack.getItem()).getNamespace())) {
            return LazyOptional.empty();
        }
        if (capability == ForgeCapabilities.FLUID_HANDLER_ITEM || capability == ForgeCapabilities.FLUID_HANDLER) {
            StackSlot slot = new StackSlot(stack);
            ContainerItemContext context = ContainerItemContext.ofSingleSlot(slot);
            return context.find(FluidStorage.ITEM) == null ? LazyOptional.empty() : (LazyOptional<T>) LazyOptional.of(() -> new StorageFluidHandlerItem(slot, context));
        } else if (capability == ForgeCapabilities.ENERGY) {
            return (LazyOptional<T>) FabricEnergy.foreignEnergy(ContainerItemContext.ofSingleSlot(new StackSlot(stack)));
        }
        return LazyOptional.empty();
    }

    /**
     * Runs an operation in a transaction that commits only when {@code simulate} is false, nested in the current
     * thread's transaction if there is one.
     */
    static long transact(boolean simulate, ToLongFunction<Transaction> operation) {
        TransactionContext current = Transaction.getCurrentUnsafe();
        try (Transaction transaction = current == null ? Transaction.openOuter() : current.openNested()) {
            long result = operation.applyAsLong(transaction);
            if (!simulate) {
                transaction.commit();
            }
            return result;
        }
    }

    static int clampToInt(long value) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }

    private static Storage<ItemVariant> asStorage(IItemHandler handler) {
        List<HandlerSlotStorage> slots = new ArrayList<>(handler.getSlots());
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            slots.add(new HandlerSlotStorage(handler, slot));
        }
        return new CombinedStorage<>(slots);
    }

    static FluidStack toStack(FluidVariant variant, int amount) {
        return variant.isBlank() || amount <= 0 ? FluidStack.EMPTY : new FluidStack(variant.getFluid(), amount, variant.copyNbt());
    }

    static FluidVariant toVariant(FluidStack stack) {
        return stack.isEmpty() ? FluidVariant.blank() : FluidVariant.of(stack.getFluid(), stack.hasTag() ? stack.getTag() : null);
    }

    /**
     * What a transaction moved into (positive) or out of (negative) a handler and has not applied yet.
     */
    private record Pending<V>(@Nullable V resource, long amount) {

        static <V> Pending<V> none() {
            return new Pending<>(null, 0);
        }
    }

    private static final class HandlerSlotStorage extends SnapshotParticipant<Pending<ItemVariant>> implements SingleSlotStorage<ItemVariant> {

        private final IItemHandler handler;
        private final int slot;
        private Pending<ItemVariant> pending = Pending.none();

        HandlerSlotStorage(IItemHandler handler, int slot) {
            this.handler = handler;
            this.slot = slot;
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            long already = Math.max(0, pending.amount());
            if (pending.amount() < 0 || (already > 0 && !resource.equals(pending.resource()))) {
                return 0;
            }
            int total = clampToInt(already + maxAmount);
            ItemStack remainder = handler.insertItem(slot, resource.toStack(total), true);
            long accepted = Math.min(maxAmount, total - remainder.getCount() - already);
            if (accepted <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            pending = new Pending<>(resource, already + accepted);
            return accepted;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (pending.amount() > 0 || !resource.matches(handler.getStackInSlot(slot))) {
                return 0;
            }
            long already = -pending.amount();
            ItemStack extracted = handler.extractItem(slot, clampToInt(already + maxAmount), true);
            if (!resource.matches(extracted)) {
                return 0;
            }
            long accepted = Math.min(maxAmount, extracted.getCount() - already);
            if (accepted <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            pending = new Pending<>(resource, -(already + accepted));
            return accepted;
        }

        @Override
        public boolean isResourceBlank() {
            return getResource().isBlank();
        }

        @Override
        public ItemVariant getResource() {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                return pending.amount() > 0 ? pending.resource() : ItemVariant.blank();
            }
            return stack.getCount() + pending.amount() > 0 ? ItemVariant.of(stack) : ItemVariant.blank();
        }

        @Override
        public long getAmount() {
            return Math.max(0, handler.getStackInSlot(slot).getCount() + pending.amount());
        }

        @Override
        public long getCapacity() {
            ItemVariant resource = getResource();
            int limit = handler.getSlotLimit(slot);
            return resource.isBlank() ? limit : Math.min(limit, resource.getItem().getMaxStackSize());
        }

        @Override
        protected Pending<ItemVariant> createSnapshot() {
            return pending;
        }

        @Override
        protected void readSnapshot(Pending<ItemVariant> snapshot) {
            pending = snapshot;
        }

        @Override
        protected void onFinalCommit() {
            Pending<ItemVariant> committed = pending;
            pending = Pending.none();
            if (committed.amount() > 0 && committed.resource() != null) {
                handler.insertItem(slot, committed.resource().toStack(clampToInt(committed.amount())), false);
            } else if (committed.amount() < 0) {
                handler.extractItem(slot, clampToInt(-committed.amount()), false);
            }
        }
    }

    private static final class HandlerFluidStorage extends SnapshotParticipant<Pending<FluidVariant>> implements Storage<FluidVariant> {

        private final IFluidHandler handler;
        private Pending<FluidVariant> pending = Pending.none();

        HandlerFluidStorage(IFluidHandler handler) {
            this.handler = handler;
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            long already = Math.max(0, pending.amount());
            if (pending.amount() < 0 || (already > 0 && !resource.equals(pending.resource()))) {
                return 0;
            }
            int total = clampToInt(already + maxAmount / DROPLETS_PER_MB);
            long accepted = Math.min(maxAmount / DROPLETS_PER_MB, handler.fill(toStack(resource, total), IFluidHandler.FluidAction.SIMULATE) - already);
            if (accepted <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            pending = new Pending<>(resource, already + accepted);
            return accepted * DROPLETS_PER_MB;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            long already = Math.max(0, -pending.amount());
            if (pending.amount() > 0 || (already > 0 && !resource.equals(pending.resource()))) {
                return 0;
            }
            int total = clampToInt(already + maxAmount / DROPLETS_PER_MB);
            FluidStack drained = handler.drain(toStack(resource, total), IFluidHandler.FluidAction.SIMULATE);
            if (drained.isEmpty() || !resource.isOf(drained.getFluid())) {
                return 0;
            }
            long accepted = Math.min(maxAmount / DROPLETS_PER_MB, drained.getAmount() - already);
            if (accepted <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            pending = new Pending<>(resource, -(already + accepted));
            return accepted * DROPLETS_PER_MB;
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            List<StorageView<FluidVariant>> views = new ArrayList<>(handler.getTanks());
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                views.add(new TankView(this, tank));
            }
            return views.iterator();
        }

        @Override
        protected Pending<FluidVariant> createSnapshot() {
            return pending;
        }

        @Override
        protected void readSnapshot(Pending<FluidVariant> snapshot) {
            pending = snapshot;
        }

        @Override
        protected void onFinalCommit() {
            Pending<FluidVariant> committed = pending;
            pending = Pending.none();
            if (committed.resource() == null || committed.amount() == 0) {
                return;
            }
            if (committed.amount() > 0) {
                handler.fill(toStack(committed.resource(), clampToInt(committed.amount())), IFluidHandler.FluidAction.EXECUTE);
            } else {
                handler.drain(toStack(committed.resource(), clampToInt(-committed.amount())), IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    private record TankView(HandlerFluidStorage storage, int tank) implements StorageView<FluidVariant> {

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            return resource.equals(getResource()) ? storage.extract(resource, maxAmount, transaction) : 0;
        }

        @Override
        public boolean isResourceBlank() {
            return storage.handler.getFluidInTank(tank).isEmpty();
        }

        @Override
        public FluidVariant getResource() {
            return toVariant(storage.handler.getFluidInTank(tank));
        }

        @Override
        public long getAmount() {
            return storage.handler.getFluidInTank(tank).getAmount() * DROPLETS_PER_MB;
        }

        @Override
        public long getCapacity() {
            return storage.handler.getTankCapacity(tank) * DROPLETS_PER_MB;
        }
    }

    private record StorageItemHandler(Storage<ItemVariant> storage) implements IItemHandler {

        @Override
        public int getSlots() {
            int count = 0;
            for (StorageView<ItemVariant> ignored : storage) {
                count++;
            }
            return Math.max(1, count);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            StorageView<ItemVariant> view = view(slot);
            return view == null || view.isResourceBlank() ? ItemStack.EMPTY : view.getResource().toStack(clampToInt(view.getAmount()));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !storage.supportsInsertion()) {
                return stack;
            }
            Storage<ItemVariant> target = view(slot) instanceof SingleSlotStorage<ItemVariant> single ? single : storage;
            ItemVariant resource = ItemVariant.of(stack);
            long inserted = transact(simulate, transaction -> target.insert(resource, stack.getCount(), transaction));
            if (inserted <= 0) {
                return stack;
            }
            return inserted >= stack.getCount() ? ItemStack.EMPTY : resource.toStack(stack.getCount() - (int) inserted);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            StorageView<ItemVariant> view = view(slot);
            if (amount <= 0 || view == null || view.isResourceBlank()) {
                return ItemStack.EMPTY;
            }
            ItemVariant resource = view.getResource();
            long extracted = transact(simulate, transaction -> view.extract(resource, amount, transaction));
            return extracted > 0 ? resource.toStack((int) extracted) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            StorageView<ItemVariant> view = view(slot);
            return view == null ? 64 : clampToInt(view.getCapacity());
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }

        @Nullable
        private StorageView<ItemVariant> view(int slot) {
            int index = 0;
            for (StorageView<ItemVariant> view : storage) {
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
            for (StorageView<FluidVariant> ignored : storage) {
                count++;
            }
            return count;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            StorageView<FluidVariant> view = view(tank);
            return view == null || view.isResourceBlank() ? FluidStack.EMPTY : toStack(view.getResource(), clampToInt(view.getAmount() / DROPLETS_PER_MB));
        }

        @Override
        public int getTankCapacity(int tank) {
            StorageView<FluidVariant> view = view(tank);
            return view == null ? 0 : clampToInt(view.getCapacity() / DROPLETS_PER_MB);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !storage.supportsInsertion()) {
                return 0;
            }
            FluidVariant variant = toVariant(resource);
            long requested = resource.getAmount() * DROPLETS_PER_MB;
            return (int) (transact(action.simulate(), transaction -> {
                long possible = storage.simulateInsert(variant, requested, transaction);
                long aligned = possible - possible % DROPLETS_PER_MB;
                return aligned > 0 ? storage.insert(variant, aligned, transaction) : 0;
            }) / DROPLETS_PER_MB);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !storage.supportsExtraction()) {
                return FluidStack.EMPTY;
            }
            return drain(toVariant(resource), resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0 || !storage.supportsExtraction()) {
                return FluidStack.EMPTY;
            }
            FluidVariant variant = StorageUtil.findExtractableResource(storage, null);
            return variant == null ? FluidStack.EMPTY : drain(variant, maxDrain, action);
        }

        private FluidStack drain(FluidVariant variant, int amount, FluidAction action) {
            long requested = amount * DROPLETS_PER_MB;
            long drained = transact(action.simulate(), transaction -> {
                long possible = storage.simulateExtract(variant, requested, transaction);
                long aligned = possible - possible % DROPLETS_PER_MB;
                return aligned > 0 ? storage.extract(variant, aligned, transaction) : 0;
            });
            return drained > 0 ? toStack(variant, (int) (drained / DROPLETS_PER_MB)) : FluidStack.EMPTY;
        }

        @Nullable
        private StorageView<FluidVariant> view(int tank) {
            int index = 0;
            for (StorageView<FluidVariant> view : storage) {
                if (index++ == tank) {
                    return view;
                }
            }
            return null;
        }
    }

    /**
     * An item's fluid storage as Forge's item fluid handler. The storage is looked up again for every operation, since
     * emptying or filling a container can turn it into an item with a different storage.
     */
    private record StorageFluidHandlerItem(StackSlot slot, ContainerItemContext context) implements IFluidHandlerItem {

        private StorageFluidHandler handler() {
            Storage<FluidVariant> storage = context.find(FluidStorage.ITEM);
            return new StorageFluidHandler(storage == null ? Storage.empty() : storage);
        }

        @Override
        public ItemStack getContainer() {
            return slot.getStack();
        }

        @Override
        public int getTanks() {
            return handler().getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return handler().getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return handler().getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return handler().fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return handler().drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return handler().drain(maxDrain, action);
        }
    }

    /**
     * One of the port's fluid items as a Transfer API storage. Only whole millibuckets move, at
     * {@link #DROPLETS_PER_MB} droplets each. Forge applies the change immediately, so it is applied to a copy of the
     * stack and exchanged into the context; aborting the transaction drops the copy. {@link IFluidHandlerItem#getContainer}
     * is what gets exchanged when the item itself changes.
     */
    private static final class ItemFluidStorage implements Storage<FluidVariant> {

        private final ContainerItemContext context;

        private ItemFluidStorage(ContainerItemContext context) {
            this.context = context;
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int millibuckets = clampToInt(maxAmount / DROPLETS_PER_MB);
            if (millibuckets <= 0) {
                return 0;
            }
            Held held = held();
            if (held == null) {
                return 0;
            }
            int filled = held.handler.fill(toStack(resource, millibuckets), IFluidHandler.FluidAction.EXECUTE);
            return filled > 0 && commitItem(context, held.result(), transaction) ? filled * DROPLETS_PER_MB : 0;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int millibuckets = clampToInt(maxAmount / DROPLETS_PER_MB);
            if (millibuckets <= 0) {
                return 0;
            }
            Held held = held();
            if (held == null) {
                return 0;
            }
            FluidStack drained = held.handler.drain(toStack(resource, millibuckets), IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty() || !resource.isOf(drained.getFluid()) || !commitItem(context, held.result(), transaction)) {
                return 0;
            }
            return drained.getAmount() * DROPLETS_PER_MB;
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            Held held = held();
            if (held == null) {
                return List.<StorageView<FluidVariant>>of().iterator();
            }
            List<StorageView<FluidVariant>> views = new ArrayList<>(held.handler.getTanks());
            for (int tank = 0; tank < held.handler.getTanks(); tank++) {
                views.add(new ItemTankView(tank));
            }
            return views.iterator();
        }

        @Nullable
        private Held held() {
            if (context.getItemVariant().isBlank()) {
                return null;
            }
            int count = clampToInt(context.getAmount());
            if (count <= 0) {
                return null;
            }
            ItemStack stack = context.getItemVariant().toStack(count);
            IFluidHandler handler = fluidHandler(stack);
            return handler == null ? null : new Held(stack, handler);
        }

        private final class ItemTankView implements StorageView<FluidVariant> {

            private final int tank;

            private ItemTankView(int tank) {
                this.tank = tank;
            }

            @Override
            public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
                return resource.equals(getResource()) ? ItemFluidStorage.this.extract(resource, maxAmount, transaction) : 0;
            }

            @Override
            public boolean isResourceBlank() {
                return fluid().isEmpty();
            }

            @Override
            public FluidVariant getResource() {
                FluidStack fluid = fluid();
                return fluid.isEmpty() ? FluidVariant.blank() : toVariant(fluid);
            }

            @Override
            public long getAmount() {
                return fluid().getAmount() * DROPLETS_PER_MB;
            }

            @Override
            public long getCapacity() {
                Held held = held();
                return held == null || tank >= held.handler.getTanks() ? 0 : held.handler.getTankCapacity(tank) * DROPLETS_PER_MB;
            }

            private FluidStack fluid() {
                Held held = held();
                return held == null || tank >= held.handler.getTanks() ? FluidStack.EMPTY : held.handler.getFluidInTank(tank);
            }
        }

        private record Held(ItemStack stack, IFluidHandler handler) {

            ItemStack result() {
                return handler instanceof IFluidHandlerItem item ? item.getContainer() : stack;
            }
        }
    }

    /**
     * A stack outside any inventory, as the single slot of a {@link ContainerItemContext}. Fabric replaces the stack with
     * a copy while a transaction is open; a committed change to the same item is copied back into the original stack,
     * which is where Forge's item capabilities leave it.
     */
    static final class StackSlot extends SingleStackStorage {

        private final ItemStack original;
        private ItemStack stack;

        StackSlot(ItemStack stack) {
            this.original = stack;
            this.stack = stack;
        }

        @Override
        protected ItemStack getStack() {
            return stack;
        }

        @Override
        protected void setStack(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        protected void onFinalCommit() {
            super.onFinalCommit();
            if (stack != original && stack.is(original.getItem())) {
                original.setTag(stack.getTag());
                original.setCount(stack.getCount());
                stack = original;
            }
        }
    }
}
