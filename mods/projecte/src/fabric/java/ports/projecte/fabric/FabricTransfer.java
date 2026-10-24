/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
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
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;
import ports.projecte.fabric.api.ItemStorageLookup;
import ports.projecte.lib.common.capabilities.Capability;
import ports.projecte.lib.common.capabilities.ForgeCapabilities;
import ports.projecte.lib.common.util.LazyOptional;
import ports.projecte.lib.fluids.FluidStack;
import ports.projecte.lib.fluids.capability.IFluidHandler;
import ports.projecte.lib.fluids.capability.IFluidHandlerItem;
import ports.projecte.lib.items.IItemHandler;
import ports.projecte.lib.items.IItemHandlerModifiable;

/**
 * Bridges Forge's item and fluid handlers with the Fabric Transfer API, in both directions.
 * <p>
 * Forge handlers only simulate or execute. The storages exposing ProjectE's item handlers execute each move at once,
 * so the handler's slots stay the only record of what they hold, and undo it if the transaction aborts (see
 * {@link SlotJournal}). Any number of storages looked up for the same handler, or for handlers sharing slots, therefore
 * see each other's moves. Forge measures fluids in millibuckets and Fabric in droplets; only whole millibuckets move.
 */
public final class FabricTransfer {

    static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;

    private static Set<String> ownNamespaces = Set.of();
    private static Set<BlockEntityType<?>> exported = Set.of();

    private FabricTransfer() {
    }

    /**
     * Exposes the item handlers of the block entities the port's mods register through the Transfer API.
     */
    static void registerBlockEntityLookups(Set<String> namespaces) {
        ownNamespaces = Set.copyOf(namespaces);
        BlockEntityType<?>[] types = Registry.BLOCK_ENTITY_TYPE.entrySet().stream()
              .filter(entry -> namespaces.contains(entry.getKey().location().getNamespace()))
              .map(Map.Entry::getValue)
              .toArray(BlockEntityType<?>[]::new);
        exported = Set.of(types);
        ItemStorage.SIDED.registerForBlockEntities((blockEntity, side) -> blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
              .resolve().map(FabricTransfer::asStorage).orElse(null), types);
        // No ProjectE block entity has a fluid handler or energy storage, so none is exposed: only the amulets' item
        // fluid handlers are, below, and those keep their state in the transaction-aware item context.
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
     * The item or fluid storage another mod's block entity offers through the Fabric APIs. The port's own block
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
        }
        return LazyOptional.empty();
    }

    /**
     * The item or fluid storage another mod's item, or a vanilla bucket, offers through the
     * Fabric APIs: {@link ItemStorageLookup#ITEM} and {@code FluidStorage.ITEM}. The port's own items answer through their attached capabilities, so they are never
     * looked up here. Changes to the stack happen in place, as with Forge's item capabilities;
     * {@link IFluidHandlerItem#getContainer} holds the replacement when the item itself changes, such as a bucket being emptied.
     */
    @SuppressWarnings("unchecked")
    static <T> LazyOptional<T> foreignCapability(ItemStack stack, Capability<T> capability) {
        if (ownNamespaces.contains(Registry.ITEM.getKey(stack.getItem()).getNamespace())) {
            return LazyOptional.empty();
        }
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            ContainerItemContext context = ContainerItemContext.ofSingleSlot(new StackSlot(stack));
            return context.find(ItemStorageLookup.ITEM) == null ? LazyOptional.empty()
                  : (LazyOptional<T>) LazyOptional.of(() -> new StorageItemHandler(new ItemContextStorage(context)));
        } else if (capability == ForgeCapabilities.FLUID_HANDLER_ITEM || capability == ForgeCapabilities.FLUID_HANDLER) {
            StackSlot slot = new StackSlot(stack);
            ContainerItemContext context = ContainerItemContext.ofSingleSlot(slot);
            return context.find(FluidStorage.ITEM) == null ? LazyOptional.empty() : (LazyOptional<T>) LazyOptional.of(() -> new StorageFluidHandlerItem(slot, context));
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

    /**
     * A Transfer API view of a block entity's item handler. Every ProjectE handler can set its slots, which is what
     * undoing an aborted transaction needs; a handler that cannot is not exposed rather than risk an undo that fails.
     */
    @Nullable
    private static Storage<ItemVariant> asStorage(IItemHandler handler) {
        if (!(handler instanceof IItemHandlerModifiable modifiable)) {
            return null;
        }
        List<HandlerSlotStorage> slots = new ArrayList<>(modifiable.getSlots());
        for (int slot = 0; slot < modifiable.getSlots(); slot++) {
            slots.add(new HandlerSlotStorage(modifiable, slot));
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
     * One slot of a ProjectE item handler. Moves execute on the handler at once, after the handler has simulated them,
     * and are journalled so an aborted transaction puts the slot back. Nothing is held back for commit, so there is no
     * commit-time move that could fail, and a second view of the same slot sees the first view's moves.
     */
    private static final class HandlerSlotStorage implements SingleSlotStorage<ItemVariant> {

        private final IItemHandlerModifiable handler;
        private final int slot;

        HandlerSlotStorage(IItemHandlerModifiable handler, int slot) {
            this.handler = handler;
            this.slot = slot;
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int requested = clampToInt(maxAmount);
            if (requested == 0) {
                return 0;
            }
            int accepted = requested - handler.insertItem(slot, resource.toStack(requested), true).getCount();
            if (accepted <= 0) {
                return 0;
            }
            SlotJournal.record(transaction, handler, slot);
            return accepted - handler.insertItem(slot, resource.toStack(accepted), false).getCount();
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int requested = clampToInt(maxAmount);
            if (requested == 0 || !resource.matches(handler.getStackInSlot(slot))) {
                return 0;
            }
            ItemStack simulated = handler.extractItem(slot, requested, true);
            if (simulated.isEmpty() || !resource.matches(simulated)) {
                return 0;
            }
            ItemStack before = SlotJournal.record(transaction, handler, slot);
            ItemStack extracted = handler.extractItem(slot, simulated.getCount(), false);
            if (extracted.isEmpty() || !resource.matches(extracted)) {
                // The handler disagreed with its own simulation: put the slot back and move nothing.
                handler.setStackInSlot(slot, before.copy());
                return 0;
            }
            return extracted.getCount();
        }

        @Override
        public boolean isResourceBlank() {
            return handler.getStackInSlot(slot).isEmpty();
        }

        @Override
        public ItemVariant getResource() {
            return ItemVariant.of(handler.getStackInSlot(slot));
        }

        @Override
        public long getAmount() {
            return handler.getStackInSlot(slot).getCount();
        }

        @Override
        public long getCapacity() {
            ItemStack stack = handler.getStackInSlot(slot);
            int limit = handler.getSlotLimit(slot);
            return stack.isEmpty() ? limit : Math.min(limit, stack.getMaxStackSize());
        }
    }

    /**
     * The undo log for slot changes made inside Fabric transactions: each entry is a slot's contents before one change.
     * There is one log per open transaction. An abort restores its entries newest first; a nested commit hands them to
     * the enclosing transaction. A single log, rather than a snapshot per storage, keeps the undo order exact even when
     * several storages (two lookups, or two sides' handlers over the same slots) change the same slot.
     */
    private static final class SlotJournal implements TransactionContext.CloseCallback {

        private static final ThreadLocal<Map<TransactionContext, SlotJournal>> OPEN = ThreadLocal.withInitial(IdentityHashMap::new);

        private final List<Entry> entries = new ArrayList<>();

        /** Records {@code slot}'s contents before a change and returns them. */
        static ItemStack record(TransactionContext transaction, IItemHandlerModifiable handler, int slot) {
            ItemStack before = handler.getStackInSlot(slot).copy();
            journal(transaction).entries.add(new Entry(handler, slot, before));
            return before;
        }

        private static SlotJournal journal(TransactionContext transaction) {
            return OPEN.get().computeIfAbsent(transaction, open -> {
                SlotJournal journal = new SlotJournal();
                open.addCloseCallback(journal);
                return journal;
            });
        }

        @Override
        public void onClose(TransactionContext transaction, TransactionContext.Result result) {
            OPEN.get().remove(transaction);
            if (result.wasAborted()) {
                for (int i = entries.size() - 1; i >= 0; i--) {
                    Entry entry = entries.get(i);
                    entry.handler().setStackInSlot(entry.slot(), entry.before().copy());
                }
            } else if (transaction.nestingDepth() > 0) {
                journal(transaction.getOpenTransaction(transaction.nestingDepth() - 1)).entries.addAll(entries);
            }
        }

        private record Entry(IItemHandlerModifiable handler, int slot, ItemStack before) {
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
     * The item storage another mod's item holds, looked up again for every operation, since moving items in or out can
     * turn it into an item with a different storage.
     */
    private record ItemContextStorage(ContainerItemContext context) implements Storage<ItemVariant> {

        private Storage<ItemVariant> storage() {
            Storage<ItemVariant> storage = context.find(ItemStorageLookup.ITEM);
            return storage == null ? Storage.empty() : storage;
        }

        @Override
        public boolean supportsInsertion() {
            return storage().supportsInsertion();
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return storage().insert(resource, maxAmount, transaction);
        }

        @Override
        public boolean supportsExtraction() {
            return storage().supportsExtraction();
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return storage().extract(resource, maxAmount, transaction);
        }

        @Override
        public Iterator<StorageView<ItemVariant>> iterator() {
            return storage().iterator();
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
