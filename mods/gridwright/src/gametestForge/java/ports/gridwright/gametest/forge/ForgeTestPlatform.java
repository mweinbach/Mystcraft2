package ports.gridwright.gametest.forge;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import ports.gridwright.Gridwright;
import ports.gridwright.gametest.TestPlatform;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Forge Energy and item handler capabilities, plus a test-only Forge Energy sink on vanilla barrels. */
final class ForgeTestPlatform implements TestPlatform {

    private static final Map<BlockEntity, EnergyStorage> SINKS = Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean sinkRegistered;

    static synchronized void registerEnergySink() {
        if (sinkRegistered) return;
        sinkRegistered = true;
        ResourceLocation id = new ResourceLocation(Gridwright.MOD_ID, "test_energy_sink");
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, (AttachCapabilitiesEvent<BlockEntity> event) -> {
            if (!(event.getObject() instanceof BarrelBlockEntity barrel)) return;
            EnergyStorage sink = new EnergyStorage(Integer.MAX_VALUE);
            SINKS.put(barrel, sink);
            LazyOptional<IEnergyStorage> optional = LazyOptional.of(() -> sink);
            event.addCapability(id, new ICapabilityProvider() {
                @Override
                public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                    return cap == ForgeCapabilities.ENERGY ? optional.cast() : LazyOptional.empty();
                }
            });
        });
    }

    @Override
    public EnergyAccess energy(BlockEntity blockEntity, Direction side) {
        IEnergyStorage energy = blockEntity.getCapability(ForgeCapabilities.ENERGY, side)
                .orElseThrow(() -> new GameTestAssertException("Missing energy capability on " + side));
        return new EnergyAccess() {
            @Override public long stored() { return energy.getEnergyStored(); }
            @Override public long capacity() { return energy.getMaxEnergyStored(); }
            @Override public long extract(long amount, boolean simulate) { return energy.extractEnergy((int) Math.min(Integer.MAX_VALUE, amount), simulate); }
            @Override public long insert(long amount, boolean simulate) { return energy.receiveEnergy((int) Math.min(Integer.MAX_VALUE, amount), simulate); }
        };
    }

    @Override
    public ItemAccess items(BlockEntity blockEntity, Direction side) {
        IItemHandler items = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
                .orElseThrow(() -> new GameTestAssertException("Missing item handler on " + side));
        return new ItemAccess() {
            @Override public ItemStack insert(ItemStack stack) { return ItemHandlerHelper.insertItem(items, stack, false); }
            @Override public ItemStack extract(int slot, int amount, boolean simulate) { return items.extractItem(slot, amount, simulate); }
        };
    }

    @Override
    public long sunk(BlockEntity barrel) {
        EnergyStorage sink = SINKS.get(barrel);
        if (sink == null) throw new GameTestAssertException("No test energy sink on " + barrel);
        return sink.getEnergyStored();
    }
}
