package ports.gridwright.forge;

import ports.gridwright.Gridwright;
import ports.gridwright.item.AngelRingFlight;
import ports.gridwright.storage.DrumBlockEntity;
import ports.gridwright.storage.FluidTank;
import ports.gridwright.storage.SoundMuffling;
import ports.gridwright.storage.TrashCanBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Forge side of the phase 2b utilities: the Angel Ring's per-tick flight check, sound muffling, a fluid
 * handler on drums and the void handlers on trash cans.
 */
public final class ForgeUtilityEvents {

    private static final ResourceLocation STORAGE_ID = new ResourceLocation(Gridwright.MOD_ID, "utility_storage");

    private ForgeUtilityEvents() { }

    static void register() {
        MinecraftForge.EVENT_BUS.addListener(ForgeUtilityEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ForgeUtilityEvents::onSoundAtPosition);
        MinecraftForge.EVENT_BUS.addListener(ForgeUtilityEvents::onSoundAtEntity);
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, ForgeUtilityEvents::onAttachBlockEntity);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) AngelRingFlight.tick(event.getServer());
    }

    private static void onSoundAtPosition(PlayLevelSoundEvent.AtPosition event) {
        Vec3 at = event.getPosition();
        event.setNewVolume(SoundMuffling.volume(event.getLevel(), at.x, at.y, at.z, event.getNewVolume()));
    }

    private static void onSoundAtEntity(PlayLevelSoundEvent.AtEntity event) {
        Entity entity = event.getEntity();
        event.setNewVolume(SoundMuffling.volume(event.getLevel(), entity.getX(), entity.getY(), entity.getZ(), event.getNewVolume()));
    }

    private static void onAttachBlockEntity(AttachCapabilitiesEvent<BlockEntity> event) {
        Object handler;
        Capability<?> capability;
        if (event.getObject() instanceof DrumBlockEntity drum) {
            capability = ForgeCapabilities.FLUID_HANDLER;
            handler = new TankHandler(drum);
        } else if (event.getObject() instanceof TrashCanBlockEntity can) {
            switch (can.kind()) {
                case ITEM -> { capability = ForgeCapabilities.ITEM_HANDLER; handler = new VoidItems(can); }
                case FLUID -> { capability = ForgeCapabilities.FLUID_HANDLER; handler = new VoidFluid(can); }
                case ENERGY -> { capability = ForgeCapabilities.ENERGY; handler = new VoidEnergy(can); }
                default -> { return; }
            }
        } else {
            return;
        }
        Provider provider = new Provider(capability, handler);
        event.addCapability(STORAGE_ID, provider);
        event.addListener(provider::invalidate);
    }

    /** Offers one handler for one capability on every side. */
    private static final class Provider implements ICapabilityProvider {
        private final Capability<?> capability;
        private final Object handler;
        private LazyOptional<Object> optional;

        Provider(Capability<?> capability, Object handler) {
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
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, value));
    }

    /** A drum's tank as a single-tank Forge fluid handler. */
    private record TankHandler(DrumBlockEntity drum) implements IFluidHandler {
        // Forge attaches capabilities inside BlockEntity's constructor, before the drum's own fields
        // are set, so the tank is looked up on every call rather than captured here.
        private FluidTank tank() { return drum.tank(); }

        @Override public int getTanks() { return 1; }

        @Override
        public @NotNull FluidStack getFluidInTank(int index) {
            return tank().isEmpty() ? FluidStack.EMPTY : new FluidStack(tank().fluid(), clamp(tank().amount()), tank().tag());
        }

        @Override public int getTankCapacity(int index) { return clamp(tank().capacity()); }

        @Override
        public boolean isFluidValid(int index, @NotNull FluidStack stack) {
            return FluidTank.storable(stack.getFluid());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            int filled = clamp(tank().insert(resource.getFluid(), resource.getTag(), resource.getAmount(), action.simulate()));
            if (filled > 0 && action.execute()) tank().changed();
            return filled;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !tank().holds(resource.getFluid(), resource.getTag())) return FluidStack.EMPTY;
            return drain(resource.getAmount(), action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            if (tank().isEmpty() || maxDrain <= 0) return FluidStack.EMPTY;
            FluidStack drained = new FluidStack(tank().fluid(), 0, tank().tag());
            drained.setAmount(clamp(tank().extract(maxDrain, action.simulate())));
            if (drained.getAmount() > 0 && action.execute()) tank().changed();
            return drained.getAmount() > 0 ? drained : FluidStack.EMPTY;
        }
    }

    /** One always-empty slot that destroys everything inserted. */
    private record VoidItems(TrashCanBlockEntity can) implements IItemHandler {
        @Override public int getSlots() { return 1; }
        @Override public @NotNull ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!simulate) can.destroy(stack.getCount());
            return ItemStack.EMPTY;
        }

        @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return true; }
    }

    /** An endless, always-empty tank. */
    private record VoidFluid(TrashCanBlockEntity can) implements IFluidHandler {
        @Override public int getTanks() { return 1; }
        @Override public @NotNull FluidStack getFluidInTank(int index) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int index) { return Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int index, @NotNull FluidStack stack) { return true; }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            if (action.execute()) can.destroy(resource.getAmount());
            return resource.getAmount();
        }

        @Override public @NotNull FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public @NotNull FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    }

    /** Accepts any amount of Forge Energy and destroys it. */
    private record VoidEnergy(TrashCanBlockEntity can) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int accepted = Math.max(0, maxReceive);
            if (!simulate) can.destroy(accepted);
            return accepted;
        }

        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return Integer.MAX_VALUE; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    }
}
