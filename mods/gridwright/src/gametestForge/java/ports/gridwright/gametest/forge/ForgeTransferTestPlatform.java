package ports.gridwright.gametest.forge;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnchantmentTableBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import ports.gridwright.Gridwright;
import ports.gridwright.gametest.TransferTestPlatform;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Forge fixtures: a fluid handler capability on jukeboxes, a two-tank one on enchanting tables and a
 * cancelling block-break listener.
 */
final class ForgeTransferTestPlatform implements TransferTestPlatform {

    private static final Map<BlockEntity, FluidTank> TANKS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<BlockEntity, TwoTanks> MULTI_TANKS = Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean registered;

    static synchronized void register() {
        if (registered) return;
        registered = true;
        ResourceLocation id = new ResourceLocation(Gridwright.MOD_ID, "test_tank");
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, (AttachCapabilitiesEvent<BlockEntity> event) -> {
            IFluidHandler handler;
            if (event.getObject() instanceof JukeboxBlockEntity jukebox) {
                FluidTank tank = new FluidTank((int) TANK_MILLIBUCKETS);
                TANKS.put(jukebox, tank);
                handler = tank;
            } else if (event.getObject() instanceof EnchantmentTableBlockEntity table) {
                TwoTanks tanks = new TwoTanks();
                MULTI_TANKS.put(table, tanks);
                handler = tanks;
            } else {
                return;
            }
            LazyOptional<IFluidHandler> optional = LazyOptional.of(() -> handler);
            event.addCapability(id, new ICapabilityProvider() {
                @Override
                public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
                    return cap == ForgeCapabilities.FLUID_HANDLER ? optional.cast() : LazyOptional.empty();
                }
            });
        });
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (PROTECTED.contains(event.getPos())) event.setCanceled(true);
        });
    }

    @Override
    public TankAccess tank(BlockEntity jukebox) {
        FluidTank tank = TANKS.get(jukebox);
        if (tank == null) throw new GameTestAssertException("No test tank on " + jukebox);
        return access(tank);
    }

    @Override
    public TankAccess multiTank(BlockEntity enchantingTable, int index) {
        TwoTanks tanks = MULTI_TANKS.get(enchantingTable);
        if (tanks == null) throw new GameTestAssertException("No test tanks on " + enchantingTable);
        return access(tanks.tanks[index]);
    }

    private static TankAccess access(FluidTank tank) {
        return new TankAccess() {
            @Nullable
            @Override
            public Fluid fluid() {
                return tank.getFluid().isEmpty() ? null : tank.getFluid().getFluid();
            }

            @Override
            public long millibuckets() {
                return tank.getFluidAmount();
            }

            @Override
            public void set(Fluid fluid, long millibuckets) {
                tank.setFluid(new FluidStack(fluid, (int) millibuckets));
            }
        };
    }

    /** Two independent tanks behind one handler: fills and drains go to the first tank that can. */
    private static final class TwoTanks implements IFluidHandler {
        final FluidTank[] tanks = {new FluidTank((int) TANK_MILLIBUCKETS), new FluidTank((int) TANK_MILLIBUCKETS)};

        @Override
        public int getTanks() {
            return tanks.length;
        }

        @Nonnull
        @Override
        public FluidStack getFluidInTank(int tank) {
            return tanks[tank].getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return tanks[tank].getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            return tanks[tank].isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            for (FluidTank tank : tanks) {
                int filled = tank.fill(resource, action);
                if (filled > 0) return filled;
            }
            return 0;
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            for (FluidTank tank : tanks) {
                FluidStack drained = tank.drain(resource, action);
                if (!drained.isEmpty()) return drained;
            }
            return FluidStack.EMPTY;
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (FluidTank tank : tanks) {
                FluidStack drained = tank.drain(maxDrain, action);
                if (!drained.isEmpty()) return drained;
            }
            return FluidStack.EMPTY;
        }
    }
}
