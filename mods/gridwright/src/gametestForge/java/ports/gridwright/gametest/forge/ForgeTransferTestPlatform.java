package ports.gridwright.gametest.forge;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
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

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Forge fixtures: a fluid handler capability on jukeboxes and a cancelling block-break listener. */
final class ForgeTransferTestPlatform implements TransferTestPlatform {

    private static final Map<BlockEntity, FluidTank> TANKS = Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean registered;

    static synchronized void register() {
        if (registered) return;
        registered = true;
        ResourceLocation id = new ResourceLocation(Gridwright.MOD_ID, "test_tank");
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, (AttachCapabilitiesEvent<BlockEntity> event) -> {
            if (!(event.getObject() instanceof JukeboxBlockEntity jukebox)) return;
            FluidTank tank = new FluidTank((int) TANK_MILLIBUCKETS);
            TANKS.put(jukebox, tank);
            LazyOptional<IFluidHandler> optional = LazyOptional.of(() -> tank);
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
}
