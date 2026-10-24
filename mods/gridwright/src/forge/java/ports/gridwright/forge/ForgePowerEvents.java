package ports.gridwright.forge;

import ports.gridwright.Gridwright;
import ports.gridwright.machine.GeneratorBlockEntity;
import ports.gridwright.power.ConverterBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Phase 2d Forge capabilities: Forge Energy on the power converter (insert-only or extract-only by
 * mode) and an insert-only lava fluid handler on generators (only a lava generator accepts anything).
 * AttachCapabilitiesEvent fires inside the block entity constructor, so handlers read state lazily.
 */
public final class ForgePowerEvents {

    private static final ResourceLocation CONVERTER_ID = new ResourceLocation(Gridwright.MOD_ID, "power_converter");
    private static final ResourceLocation LAVA_ID = new ResourceLocation(Gridwright.MOD_ID, "generator_lava");

    private ForgePowerEvents() { }

    static void register() {
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, ForgePowerEvents::onAttachBlockEntity);
    }

    private static void onAttachBlockEntity(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject() instanceof ConverterBlockEntity converter) {
            Provider<IEnergyStorage> provider = new Provider<>(ForgeCapabilities.ENERGY, new ConverterEnergy(converter));
            event.addCapability(CONVERTER_ID, provider);
            event.addListener(provider::invalidate);
        } else if (event.getObject() instanceof GeneratorBlockEntity generator) {
            Provider<IFluidHandler> provider = new Provider<>(ForgeCapabilities.FLUID_HANDLER, new LavaInput(generator));
            event.addCapability(LAVA_ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    private static final class Provider<H> implements ICapabilityProvider {

        private final Capability<H> capability;
        private final H handler;
        private LazyOptional<H> optional;

        Provider(Capability<H> capability, H handler) {
            this.capability = capability;
            this.handler = handler;
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            if (cap != capability) return LazyOptional.empty();
            if (optional == null) optional = LazyOptional.of(() -> handler);
            return optional.cast();
        }

        void invalidate() {
            if (optional != null) optional.invalidate();
            optional = null;
        }
    }

    private static int clamp(long value) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }

    private record ConverterEnergy(ConverterBlockEntity converter) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            long received = converter.receive(maxReceive, simulate);
            if (!simulate && received > 0) converter.markChanged();
            return clamp(received);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            long extracted = converter.extract(maxExtract, simulate);
            if (!simulate && extracted > 0) converter.markChanged();
            return clamp(extracted);
        }

        @Override public int getEnergyStored() { return clamp(converter.stored()); }
        @Override public int getMaxEnergyStored() { return clamp(converter.capacity()); }
        @Override public boolean canExtract() { return converter.canExtract(); }
        @Override public boolean canReceive() { return converter.canReceive(); }
    }

    /** Pipes may fill a lava generator's tank; nothing may drain it. */
    private record LavaInput(GeneratorBlockEntity generator) implements IFluidHandler {
        @Override public int getTanks() { return 1; }

        @Override
        public @NotNull FluidStack getFluidInTank(int index) {
            var tank = generator.lavaTank();
            return tank.isEmpty() ? FluidStack.EMPTY : new FluidStack(tank.fluid(), clamp(tank.amount()), tank.tag());
        }

        @Override public int getTankCapacity(int index) { return clamp(generator.lavaTank().capacity()); }

        @Override
        public boolean isFluidValid(int index, @NotNull FluidStack stack) {
            return generator.acceptsFluid(stack.getFluid());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            int filled = clamp(generator.insertLava(resource.getFluid(), resource.getTag(), resource.getAmount(), action.simulate()));
            if (action.execute() && filled > 0) generator.lavaTank().changed();
            return filled;
        }

        @Override public @NotNull FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public @NotNull FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    }
}
