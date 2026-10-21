/*
 * Extreme Reactors 1.19.2 port - Forge adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.forge;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidUtil;
import ports.extremereactors.corelib.capability.Capability;
import ports.extremereactors.corelib.capability.CapabilityHelper;
import ports.extremereactors.corelib.capability.ICapabilityProvider;
import ports.extremereactors.corelib.capability.LazyOptional;
import ports.extremereactors.corelib.energy.IEnergyStorage;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandler;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandlerModifiable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Bridges the corelib item, fluid and energy handlers, and the optional mods' handlers {@link #register registered}
 * here, with Forge capabilities, in both directions. The corelib interfaces mirror Forge's, so the adapters only
 * delegate; fluid amounts are millibuckets on both sides.
 * <p>
 * Every adapter is invalidated together with the handler it wraps.
 */
public final class ForgeTransfer {

    /**
     * Another mod's Forge capability and the corelib capability that stands for it, with the adapters between their
     * handlers.
     */
    public record ForeignCapability<C, F>(net.minecraftforge.common.capabilities.Capability<F> forge,
                                          Capability<C> corelib, Function<C, F> asForge, Function<F, C> asCorelib) {
    }

    /** Bridge an optional mod's capability. Call it before any world loads. */
    public static void register(final ForeignCapability<?, ?> capability) {
        FOREIGN_CAPABILITIES.add(capability);
    }

    /**
     * The Forge view of what one of our block entities exposes for a Forge capability.
     *
     * @param exposed the adapters already handed out by this block entity, keyed by the corelib handler they wrap
     */
    public static <T> net.minecraftforge.common.util.LazyOptional<T> expose(
            final ICapabilityProvider provider,
            final Map<LazyOptional<?>, net.minecraftforge.common.util.LazyOptional<?>> exposed,
            final net.minecraftforge.common.capabilities.Capability<T> capability, @Nullable final Direction side) {

        final Capability<?> ours = corelibCapabilityOf(capability);

        if (null == ours) {
            return net.minecraftforge.common.util.LazyOptional.empty();
        }

        final LazyOptional<?> handler = provider.getCapability(ours, side);
        final Object resolved = handler.orElse(null);

        if (null == resolved) {
            return net.minecraftforge.common.util.LazyOptional.empty();
        }

        synchronized (exposed) {

            net.minecraftforge.common.util.LazyOptional<?> adapter = exposed.get(handler);

            if (null == adapter) {

                final Object wrapped = asForge(capability, resolved);
                final net.minecraftforge.common.util.LazyOptional<Object> created =
                        net.minecraftforge.common.util.LazyOptional.of(() -> wrapped);

                exposed.put(handler, created);
                handler.addListener(self -> {

                    synchronized (exposed) {
                        exposed.remove(self);
                    }

                    created.invalidate();
                });

                adapter = created;
            }

            return adapter.cast();
        }
    }

    /**
     * Invalidate every adapter a block entity handed out, for example because it was removed.
     */
    public static void invalidateAll(final Map<LazyOptional<?>, net.minecraftforge.common.util.LazyOptional<?>> exposed) {

        final List<net.minecraftforge.common.util.LazyOptional<?>> adapters;

        synchronized (exposed) {

            adapters = new ArrayList<>(exposed.values());
            exposed.clear();
        }

        adapters.forEach(net.minecraftforge.common.util.LazyOptional::invalidate);
    }

    public static Map<LazyOptional<?>, net.minecraftforge.common.util.LazyOptional<?>> newExposedMap() {
        return new WeakHashMap<>();
    }

    /**
     * The corelib view of a handler exposed through Forge capabilities by a block entity that is not ours.
     */
    @SuppressWarnings("unchecked")
    public static <T> LazyOptional<T> foreignCapability(@Nullable final BlockEntity blockEntity,
                                                        final Capability<T> capability, @Nullable final Direction side) {

        if (null == blockEntity) {
            return LazyOptional.empty();
        }

        if (CapabilityHelper.ITEM_HANDLER == capability) {
            return (LazyOptional<T>) adopt(blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side), ForgeTransfer::asCorelib);
        }

        if (CapabilityHelper.FLUID_HANDLER == capability) {
            return (LazyOptional<T>) adopt(blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side), ForgeTransfer::asCorelib);
        }

        if (CapabilityHelper.ENERGY == capability) {
            return (LazyOptional<T>) adopt(blockEntity.getCapability(ForgeCapabilities.ENERGY, side), ForgeTransfer::asCorelib);
        }

        for (final ForeignCapability<?, ?> foreign : FOREIGN_CAPABILITIES) {
            if (foreign.corelib() == capability) {
                return (LazyOptional<T>) adoptForeign(blockEntity, foreign, side);
            }
        }

        return LazyOptional.empty();
    }

    /**
     * An energy view of the item in a slot of one of our inventories, or null if that item holds no energy. Forge
     * item capabilities change the stack in place.
     */
    @Nullable
    public static IEnergyStorage itemEnergy(final IItemHandlerModifiable inventory, final int slot) {

        final ItemStack stack = inventory.getStackInSlot(slot);

        if (stack.isEmpty()) {
            return null;
        }

        return stack.getCapability(ForgeCapabilities.ENERGY).map(ForgeTransfer::asCorelib).orElse(null);
    }

    public static boolean interactWithFluidHandler(final Player player, final InteractionHand hand,
                                                   final IFluidHandler handler) {
        return FluidUtil.interactWithFluidHandler(player, hand, asForge(handler));
    }

    //region adapters lookup

    @Nullable
    private static Capability<?> corelibCapabilityOf(final net.minecraftforge.common.capabilities.Capability<?> capability) {

        if (ForgeCapabilities.ITEM_HANDLER == capability) {
            return CapabilityHelper.ITEM_HANDLER;
        }

        if (ForgeCapabilities.FLUID_HANDLER == capability) {
            return CapabilityHelper.FLUID_HANDLER;
        }

        if (ForgeCapabilities.ENERGY == capability) {
            return CapabilityHelper.ENERGY;
        }

        for (final ForeignCapability<?, ?> foreign : FOREIGN_CAPABILITIES) {
            if (foreign.forge() == capability) {
                return foreign.corelib();
            }
        }

        return null;
    }

    private static Object asForge(final net.minecraftforge.common.capabilities.Capability<?> capability, final Object handler) {

        if (ForgeCapabilities.ITEM_HANDLER == capability) {
            return asForge((IItemHandler) handler);
        }

        if (ForgeCapabilities.FLUID_HANDLER == capability) {
            return asForge((IFluidHandler) handler);
        }

        if (ForgeCapabilities.ENERGY == capability) {
            return asForge((IEnergyStorage) handler);
        }

        for (final ForeignCapability<?, ?> foreign : FOREIGN_CAPABILITIES) {
            if (foreign.forge() == capability) {
                return asForeign(foreign, handler);
            }
        }

        throw new IllegalArgumentException("No adapter for " + capability.getName());
    }

    @SuppressWarnings("unchecked")
    private static <C, F> F asForeign(final ForeignCapability<C, F> foreign, final Object handler) {
        return foreign.asForge().apply((C) handler);
    }

    private static <C, F> LazyOptional<C> adoptForeign(final BlockEntity blockEntity,
                                                       final ForeignCapability<C, F> foreign,
                                                       @Nullable final Direction side) {
        return adopt(blockEntity.getCapability(foreign.forge(), side), foreign.asCorelib());
    }

    /**
     * Wrap a Forge handler once per Forge {@code LazyOptional}, so repeated lookups of the same neighbour neither
     * create new adapters nor pile up invalidation listeners on it.
     */
    private static <F, C> LazyOptional<C> adopt(final net.minecraftforge.common.util.LazyOptional<F> theirs,
                                                final Function<F, C> adapter) {

        final F resolved = theirs.orElse(null);

        if (null == resolved) {
            return LazyOptional.empty();
        }

        synchronized (ADOPTED) {

            @SuppressWarnings("unchecked")
            LazyOptional<C> ours = (LazyOptional<C>) ADOPTED.get(theirs);

            if (null == ours) {

                final C wrapped = adapter.apply(resolved);
                final LazyOptional<C> created = LazyOptional.of(() -> wrapped);

                ADOPTED.put(theirs, created);
                theirs.addListener(self -> {

                    synchronized (ADOPTED) {
                        ADOPTED.remove(self);
                    }

                    created.invalidate();
                });

                ours = created;
            }

            return ours;
        }
    }

    private static final Map<net.minecraftforge.common.util.LazyOptional<?>, LazyOptional<?>> ADOPTED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final List<ForeignCapability<?, ?>> FOREIGN_CAPABILITIES = new CopyOnWriteArrayList<>();

    //endregion
    //region items

    static net.minecraftforge.items.IItemHandler asForge(final IItemHandler handler) {

        if (handler instanceof ForgeItems foreign) {
            return foreign.handler();
        }

        return handler instanceof IItemHandlerModifiable modifiable ? new ModifiableItems(modifiable) : new Items(handler);
    }

    static IItemHandler asCorelib(final net.minecraftforge.items.IItemHandler handler) {

        if (handler instanceof Items ours) {
            return ours.handler();
        }

        return new ForgeItems(handler);
    }

    private static class Items implements net.minecraftforge.items.IItemHandler {

        Items(final IItemHandler handler) {
            this._handler = handler;
        }

        IItemHandler handler() {
            return this._handler;
        }

        @Override
        public int getSlots() {
            return this._handler.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(final int slot) {
            return this._handler.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
            return this._handler.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
            return this._handler.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(final int slot) {
            return this._handler.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return this._handler.isItemValid(slot, stack);
        }

        private final IItemHandler _handler;
    }

    private static final class ModifiableItems extends Items implements net.minecraftforge.items.IItemHandlerModifiable {

        ModifiableItems(final IItemHandlerModifiable handler) {

            super(handler);
            this._modifiable = handler;
        }

        @Override
        public void setStackInSlot(final int slot, final ItemStack stack) {
            this._modifiable.setStackInSlot(slot, stack);
        }

        private final IItemHandlerModifiable _modifiable;
    }

    private record ForgeItems(net.minecraftforge.items.IItemHandler handler) implements IItemHandler {

        @Override
        public int getSlots() {
            return this.handler.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(final int slot) {
            return this.handler.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
            return this.handler.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
            return this.handler.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(final int slot) {
            return this.handler.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return this.handler.isItemValid(slot, stack);
        }
    }

    //endregion
    //region fluids

    static net.minecraftforge.fluids.capability.IFluidHandler asForge(final IFluidHandler handler) {
        return handler instanceof ForgeFluids foreign ? foreign.handler() : new Fluids(handler);
    }

    static IFluidHandler asCorelib(final net.minecraftforge.fluids.capability.IFluidHandler handler) {
        return handler instanceof Fluids ours ? ours.handler() : new ForgeFluids(handler);
    }

    static net.minecraftforge.fluids.FluidStack toForge(final FluidStack stack) {
        return stack.isEmpty() ? net.minecraftforge.fluids.FluidStack.EMPTY :
                new net.minecraftforge.fluids.FluidStack(stack.getFluid(), stack.getAmount(), stack.hasTag() ? stack.getTag() : null);
    }

    static FluidStack toCorelib(final net.minecraftforge.fluids.FluidStack stack) {
        return stack.isEmpty() ? FluidStack.EMPTY : new FluidStack(stack.getFluid(), stack.getAmount(), stack.getTag());
    }

    private static IFluidHandler.FluidAction toCorelib(final net.minecraftforge.fluids.capability.IFluidHandler.FluidAction action) {
        return action.simulate() ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
    }

    private static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction toForge(final IFluidHandler.FluidAction action) {
        return action.simulate() ? net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE :
                net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
    }

    private record Fluids(IFluidHandler handler) implements net.minecraftforge.fluids.capability.IFluidHandler {

        @Override
        public int getTanks() {
            return this.handler.getTanks();
        }

        @Override
        public net.minecraftforge.fluids.FluidStack getFluidInTank(final int tank) {
            return toForge(this.handler.getFluidInTank(tank));
        }

        @Override
        public int getTankCapacity(final int tank) {
            return this.handler.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(final int tank, final net.minecraftforge.fluids.FluidStack stack) {
            return this.handler.isFluidValid(tank, toCorelib(stack));
        }

        @Override
        public int fill(final net.minecraftforge.fluids.FluidStack resource, final FluidAction action) {
            return this.handler.fill(toCorelib(resource), toCorelib(action));
        }

        @Override
        public net.minecraftforge.fluids.FluidStack drain(final net.minecraftforge.fluids.FluidStack resource,
                                                          final FluidAction action) {
            return toForge(this.handler.drain(toCorelib(resource), toCorelib(action)));
        }

        @Override
        public net.minecraftforge.fluids.FluidStack drain(final int maxDrain, final FluidAction action) {
            return toForge(this.handler.drain(maxDrain, toCorelib(action)));
        }
    }

    private record ForgeFluids(net.minecraftforge.fluids.capability.IFluidHandler handler) implements IFluidHandler {

        @Override
        public int getTanks() {
            return this.handler.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(final int tank) {
            return toCorelib(this.handler.getFluidInTank(tank));
        }

        @Override
        public int getTankCapacity(final int tank) {
            return this.handler.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(final int tank, final FluidStack stack) {
            return this.handler.isFluidValid(tank, toForge(stack));
        }

        @Override
        public int fill(final FluidStack resource, final FluidAction action) {
            return this.handler.fill(toForge(resource), toForge(action));
        }

        @Override
        public FluidStack drain(final FluidStack resource, final FluidAction action) {
            return toCorelib(this.handler.drain(toForge(resource), toForge(action)));
        }

        @Override
        public FluidStack drain(final int maxDrain, final FluidAction action) {
            return toCorelib(this.handler.drain(maxDrain, toForge(action)));
        }
    }

    //endregion
    //region energy

    static net.minecraftforge.energy.IEnergyStorage asForge(final IEnergyStorage handler) {
        return handler instanceof ForgeEnergy foreign ? foreign.handler() : new Energy(handler);
    }

    static IEnergyStorage asCorelib(final net.minecraftforge.energy.IEnergyStorage handler) {
        return handler instanceof Energy ours ? ours.handler() : new ForgeEnergy(handler);
    }

    private record Energy(IEnergyStorage handler) implements net.minecraftforge.energy.IEnergyStorage {

        @Override
        public int receiveEnergy(final int maxReceive, final boolean simulate) {
            return this.handler.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(final int maxExtract, final boolean simulate) {
            return this.handler.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return this.handler.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return this.handler.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return this.handler.canExtract();
        }

        @Override
        public boolean canReceive() {
            return this.handler.canReceive();
        }
    }

    private record ForgeEnergy(net.minecraftforge.energy.IEnergyStorage handler) implements IEnergyStorage {

        @Override
        public int receiveEnergy(final int maxReceive, final boolean simulate) {
            return this.handler.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(final int maxExtract, final boolean simulate) {
            return this.handler.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return this.handler.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return this.handler.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return this.handler.canExtract();
        }

        @Override
        public boolean canReceive() {
            return this.handler.canReceive();
        }
    }

    //endregion

    private ForgeTransfer() {
    }
}
