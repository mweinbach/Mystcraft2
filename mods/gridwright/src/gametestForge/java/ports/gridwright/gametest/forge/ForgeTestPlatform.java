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

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Forge Energy and item handler capabilities, plus a test-only Forge Energy sink on vanilla barrels. */
final class ForgeTestPlatform implements TestPlatform {

    private static final Map<BlockEntity, EnergyStorage> SINKS = Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean sinkRegistered;

    private static final Set<BlockPos> CLAIMED = Collections.synchronizedSet(new HashSet<>());

    /** A stand-in claim mod: cancels breaking and placing at claimed positions. */
    private static boolean claimsRegistered;

    static synchronized void registerClaims() {
        if (claimsRegistered) return;
        claimsRegistered = true;
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (CLAIMED.contains(event.getPos())) event.setCanceled(true);
        });
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent event) -> {
            if (CLAIMED.contains(event.getPos())) event.setCanceled(true);
        });
    }

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

    @Override
    public FluidAccess fluids(BlockEntity blockEntity, Direction side) {
        IFluidHandler fluids = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side)
                .orElseThrow(() -> new GameTestAssertException("Missing fluid handler on " + side));
        return new FluidAccess() {
            @Override
            public long fill(Fluid fluid, long amount, boolean simulate) {
                return fluids.fill(new FluidStack(fluid, (int) Math.min(Integer.MAX_VALUE, amount)),
                        simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            }

            @Override
            public long drain(long amount, boolean simulate) {
                return fluids.drain((int) Math.min(Integer.MAX_VALUE, amount),
                        simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE).getAmount();
            }

            @Override public long amount() { return fluids.getFluidInTank(0).getAmount(); }

            @Override
            public Fluid fluid() {
                FluidStack stack = fluids.getFluidInTank(0);
                return stack.isEmpty() ? Fluids.EMPTY : stack.getFluid();
            }
        };
    }

    @Override
    public long offerItems(BlockEntity blockEntity, Direction side, ItemStack stack) {
        IItemHandler items = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
                .orElseThrow(() -> new GameTestAssertException("Missing item handler on " + side));
        return stack.getCount() - ItemHandlerHelper.insertItem(items, stack.copy(), false).getCount();
    }

    @Override
    public void protect(BlockPos pos, boolean claimed) {
        if (claimed) CLAIMED.add(pos.immutable());
        else CLAIMED.remove(pos);
    }
}
