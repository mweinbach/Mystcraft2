package com.agricraft.agricraft.common.forge.irrigation;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationTankBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes irrigation tanks as a water-only {@link IFluidHandler}, as AgriCraft 3 did.
 */
public class ForgeTankFluidHandler implements IFluidHandler {

	private static final ResourceLocation ID = new ResourceLocation(AgriApi.MOD_ID, "irrigation_tank");

	private final IrrigationTankBlockEntity tank;

	public ForgeTankFluidHandler(IrrigationTankBlockEntity tank) {
		this.tank = tank;
	}

	public static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
		if (event.getObject() instanceof IrrigationTankBlockEntity tank) {
			LazyOptional<IFluidHandler> handler = LazyOptional.of(() -> new ForgeTankFluidHandler(tank));
			event.addCapability(ID, new ICapabilityProvider() {
				@Override
				public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
					return ForgeCapabilities.FLUID_HANDLER.orEmpty(cap, handler);
				}
			});
			event.addListener(handler::invalidate);
		}
	}

	@Override
	public int getTanks() {
		return 1;
	}

	@Override
	public @NotNull FluidStack getFluidInTank(int tank) {
		return tank == 0 && this.tank.getContent() > 0 ? new FluidStack(Fluids.WATER, this.tank.getContent()) : FluidStack.EMPTY;
	}

	@Override
	public int getTankCapacity(int tank) {
		return tank == 0 ? this.tank.getCapacity() : 0;
	}

	@Override
	public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
		return tank == 0 && stack.getFluid() == Fluids.WATER;
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		return resource.getFluid() == Fluids.WATER ? this.tank.pushWater(resource.getAmount(), action.execute()) : 0;
	}

	@Override
	public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
		return resource.getFluid() == Fluids.WATER ? this.drain(resource.getAmount(), action) : FluidStack.EMPTY;
	}

	@Override
	public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
		int drained = this.tank.drainWater(maxDrain, action.execute());
		return drained > 0 ? new FluidStack(Fluids.WATER, drained) : FluidStack.EMPTY;
	}

}
