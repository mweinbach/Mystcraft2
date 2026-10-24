package ports.gridwright.forge;

import ports.gridwright.Gridwright;
import ports.gridwright.machine.EnergyBuffer;
import ports.gridwright.machine.EnergyHolder;
import ports.gridwright.machine.MachineBlockEntity;
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
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Forge capabilities on the machines: item handlers that follow each machine's face rules, and an
 * extract-only Forge Energy view of each generator's store.
 */
public final class ForgeCapabilityEvents {

    private static final ResourceLocation MACHINE_ID = new ResourceLocation(Gridwright.MOD_ID, "machine");

    private ForgeCapabilityEvents() { }

    static void register() {
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, ForgeCapabilityEvents::onAttachBlockEntity);
    }

    private static void onAttachBlockEntity(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject() instanceof MachineBlockEntity machine) {
            MachineProvider provider = new MachineProvider(machine);
            event.addCapability(MACHINE_ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    private static final class MachineProvider implements ICapabilityProvider {

        private final MachineBlockEntity machine;
        private LazyOptional<IEnergyStorage> energy;
        private final LazyOptional<?>[] items = new LazyOptional<?>[Direction.values().length + 1];

        MachineProvider(MachineBlockEntity machine) {
            this.machine = machine;
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            if (cap == ForgeCapabilities.ENERGY && machine instanceof EnergyHolder holder) {
                if (energy == null) energy = LazyOptional.of(() -> new EnergyView(holder.energy()));
                return energy.cast();
            }
            if (cap == ForgeCapabilities.ITEM_HANDLER) {
                int index = side == null ? 0 : side.ordinal() + 1;
                if (items[index] == null) {
                    items[index] = side == null
                            ? LazyOptional.of(() -> (IItemHandler) new InvWrapper(machine))
                            : LazyOptional.of(() -> (IItemHandler) new SidedInvWrapper(machine, side));
                }
                return items[index].cast();
            }
            return LazyOptional.empty();
        }

        void invalidate() {
            if (energy != null) energy.invalidate();
            energy = null;
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null) items[i].invalidate();
                items[i] = null;
            }
        }
    }

    /** Generators only give energy: other mods may extract, never insert. */
    private record EnergyView(EnergyBuffer energy) implements IEnergyStorage {
        @Override public int receiveEnergy(int maxReceive, boolean simulate) { return 0; }
        @Override public int extractEnergy(int maxExtract, boolean simulate) { return (int) energy.extract(maxExtract, simulate); }
        @Override public int getEnergyStored() { return (int) Math.min(Integer.MAX_VALUE, energy.stored()); }
        @Override public int getMaxEnergyStored() { return (int) Math.min(Integer.MAX_VALUE, energy.capacity()); }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    }
}
