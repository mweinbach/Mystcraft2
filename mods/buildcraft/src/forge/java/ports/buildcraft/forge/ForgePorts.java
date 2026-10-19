package ports.buildcraft.forge;

import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.platform.PortHolder;

/**
 * Bridges shared ports and Forge capabilities in both directions: BuildCraft block entities expose
 * {@code ENERGY}, {@code FLUID_HANDLER} and {@code ITEM_HANDLER}, and other mods' capabilities are
 * wrapped as ports.
 */
final class ForgePorts {
    private static final ResourceLocation KEY = new ResourceLocation(BuildCraft.MOD_ID, "ports");

    private ForgePorts() {}

    static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof PortHolder holder)) return;
        Provider provider = new Provider(holder, event.getObject());
        event.addCapability(KEY, provider);
        event.addListener(provider::invalidate);
    }

    private static final class Provider implements ICapabilityProvider {
        private final PortHolder holder;
        private final BlockEntity tile;
        @SuppressWarnings("unchecked")
        private final LazyOptional<?>[][] cache = new LazyOptional[3][7];

        Provider(PortHolder holder, BlockEntity tile) {
            this.holder = holder;
            this.tile = tile;
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
            int kind;
            if (capability == ForgeCapabilities.ENERGY) kind = 0;
            else if (capability == ForgeCapabilities.FLUID_HANDLER) kind = 1;
            else if (capability == ForgeCapabilities.ITEM_HANDLER) kind = 2;
            else return LazyOptional.empty();
            int index = side == null ? 6 : side.get3DDataValue();
            LazyOptional<?> cached = cache[kind][index];
            if (cached == null) {
                Object handler = create(kind, side);
                cached = handler == null ? LazyOptional.empty() : LazyOptional.of(() -> handler);
                cache[kind][index] = cached;
            }
            return cached.cast();
        }

        @Nullable
        private Object create(int kind, @Nullable Direction side) {
            switch (kind) {
                case 0 -> {
                    EnergyPort port = holder.energyPort(side);
                    return port == null ? null : new EnergyHandler(port);
                }
                case 1 -> {
                    FluidPort port = holder.fluidPort(side);
                    return port == null ? null : new FluidHandler(port);
                }
                default -> {
                    ItemPort port = holder.itemPort(side);
                    if (port != null) return new ItemHandler(port);
                    if (tile instanceof WorldlyContainer sided && side != null) return new SidedInvWrapper(sided, side);
                    if (tile instanceof Container container) return new InvWrapper(container);
                    return null;
                }
            }
        }

        void invalidate() {
            for (LazyOptional<?>[] row : cache) {
                for (LazyOptional<?> optional : row) if (optional != null) optional.invalidate();
            }
        }
    }

    // Shared ports exposed as Forge capabilities.

    private record EnergyHandler(EnergyPort port) implements IEnergyStorage {
        @Override public int receiveEnergy(int max, boolean simulate) { return (int) port.receive(max, simulate); }
        @Override public int extractEnergy(int max, boolean simulate) { return (int) port.extract(max, simulate); }
        @Override public int getEnergyStored() { return (int) Math.min(Integer.MAX_VALUE, port.stored()); }
        @Override public int getMaxEnergyStored() { return (int) Math.min(Integer.MAX_VALUE, port.capacity()); }
        @Override public boolean canExtract() { return port.canExtract(); }
        @Override public boolean canReceive() { return port.canReceive(); }
    }

    private record FluidHandler(FluidPort port) implements IFluidHandler {
        @Override
        public int getTanks() {
            return Math.max(1, port.contents().size());
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            List<FluidAmount> contents = port.contents();
            if (tank >= contents.size()) return FluidStack.EMPTY;
            FluidAmount amount = contents.get(tank);
            return amount.isEmpty() ? FluidStack.EMPTY : new FluidStack(amount.fluid(), (int) Math.min(Integer.MAX_VALUE, amount.amount()));
        }

        @Override
        public int getTankCapacity(int tank) {
            return (int) Math.min(Integer.MAX_VALUE, port.capacity(tank));
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return !stack.isEmpty() && !stack.hasTag() && port.fill(stack.getFluid(), 1, true) > 0;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || resource.hasTag()) return 0;
            return (int) port.fill(resource.getFluid(), resource.getAmount(), action.simulate());
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || resource.hasTag()) return FluidStack.EMPTY;
            return toStack(port.drain(resource.getFluid(), resource.getAmount(), action.simulate()));
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return toStack(port.drain(null, maxDrain, action.simulate()));
        }

        private static FluidStack toStack(FluidAmount amount) {
            return amount.isEmpty() ? FluidStack.EMPTY : new FluidStack(amount.fluid(), (int) amount.amount());
        }
    }

    /** Pipes and providing machines accept items through one virtual slot and hold none. */
    private record ItemHandler(ItemPort port) implements IItemHandler {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack.isEmpty() ? stack : port.insert(stack, simulate); }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return port.extract(stack -> true, amount, simulate); }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    }

    // Forge capabilities of other blocks wrapped as shared ports.

    @Nullable
    static EnergyPort findEnergy(Level level, BlockPos pos, Direction side) {
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile == null) return null;
        IEnergyStorage storage = tile.getCapability(ForgeCapabilities.ENERGY, side).resolve().orElse(null);
        if (storage == null) return null;
        return new EnergyPort() {
            @Override public long receive(long amount, boolean simulate) { return storage.receiveEnergy((int) Math.min(Integer.MAX_VALUE, amount), simulate); }
            @Override public long extract(long amount, boolean simulate) { return storage.extractEnergy((int) Math.min(Integer.MAX_VALUE, amount), simulate); }
            @Override public long stored() { return storage.getEnergyStored(); }
            @Override public long capacity() { return storage.getMaxEnergyStored(); }
            @Override public boolean canReceive() { return storage.canReceive(); }
            @Override public boolean canExtract() { return storage.canExtract(); }
        };
    }

    @Nullable
    static ItemPort findItems(Level level, BlockPos pos, Direction side) {
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile == null) return null;
        IItemHandler handler = tile.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve().orElse(null);
        if (handler == null) {
            if (tile instanceof WorldlyContainer sided) handler = new SidedInvWrapper(sided, side);
            else if (tile instanceof Container container) handler = new InvWrapper(container);
            else return null;
        }
        IItemHandler items = handler;
        return new ItemPort() {
            @Override
            public ItemStack insert(ItemStack stack, boolean simulate) {
                return ItemHandlerHelper.insertItemStacked(items, stack, simulate);
            }

            @Override
            public ItemStack extract(Predicate<ItemStack> filter, int max, boolean simulate) {
                return extractMatching(items, filter, max, simulate);
            }
        };
    }

    /** Upstream {@code ItemTransactorHelper} extraction: the first matching stack type, gathered across slots. */
    private static ItemStack extractMatching(IItemHandler items, Predicate<ItemStack> filter, int max, boolean simulate) {
        ItemStack result = ItemStack.EMPTY;
        for (int slot = 0; slot < items.getSlots() && result.getCount() < max; slot++) {
            ItemStack inSlot = items.getStackInSlot(slot);
            if (inSlot.isEmpty() || !filter.test(inSlot)) continue;
            if (!result.isEmpty() && !ItemHandlerHelper.canItemStacksStack(result, inSlot)) continue;
            int limit = Math.min(max, inSlot.getMaxStackSize()) - result.getCount();
            ItemStack extracted = items.extractItem(slot, limit, simulate);
            if (extracted.isEmpty()) continue;
            if (result.isEmpty()) result = extracted.copy();
            else result.grow(extracted.getCount());
        }
        return result;
    }

    @Nullable
    static FluidPort findFluids(Level level, BlockPos pos, Direction side) {
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile == null) return null;
        IFluidHandler handler = tile.getCapability(ForgeCapabilities.FLUID_HANDLER, side).resolve().orElse(null);
        if (handler == null) return null;
        return new FluidPort() {
            @Override
            public long fill(Fluid fluid, long amount, boolean simulate) {
                return handler.fill(new FluidStack(fluid, (int) Math.min(Integer.MAX_VALUE, amount)), action(simulate));
            }

            @Override
            public FluidAmount drain(@Nullable Fluid filter, long amount, boolean simulate) {
                int max = (int) Math.min(Integer.MAX_VALUE, amount);
                FluidStack drained = filter == null ? handler.drain(max, action(simulate)) : handler.drain(new FluidStack(filter, max), action(simulate));
                return drained.isEmpty() ? FluidAmount.EMPTY : new FluidAmount(drained.getFluid(), drained.getAmount());
            }

            @Override
            public List<FluidAmount> contents() {
                FluidAmount[] amounts = new FluidAmount[handler.getTanks()];
                for (int i = 0; i < amounts.length; i++) {
                    FluidStack stack = handler.getFluidInTank(i);
                    amounts[i] = stack.isEmpty() ? FluidAmount.EMPTY : new FluidAmount(stack.getFluid(), stack.getAmount());
                }
                return List.of(amounts);
            }

            @Override
            public long capacity(int index) {
                return handler.getTankCapacity(index);
            }
        };
    }

    private static IFluidHandler.FluidAction action(boolean simulate) {
        return simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
    }
}
