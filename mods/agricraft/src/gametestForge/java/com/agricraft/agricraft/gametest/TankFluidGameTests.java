package com.agricraft.agricraft.gametest;

import com.agricraft.agricraft.api.config.IrrigationConfig;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import static com.agricraft.agricraft.gametest.GameTestSupport.TEMPLATE;
import static com.agricraft.agricraft.gametest.GameTestSupport.check;

/** The irrigation tank through Forge's fluid capability; the Fabric twin uses Fabric Transfer. */
public class TankFluidGameTests {

	@GameTest(template = TEMPLATE)
	public static void tankExposesItsWaterToPipes(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		IrrigationTankBlockEntity tank = IrrigationGameTests.tank(helper, pos);
		IFluidHandler handler = tank.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP)
				.orElseThrow(() -> new IllegalStateException("the tank should expose a fluid handler"));
		check(handler.getTankCapacity(0) == IrrigationConfig.tankCapacity, "the handler should report the tank capacity");
		check(handler.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 0, "a tank should refuse lava");
		check(handler.fill(new FluidStack(Fluids.WATER, 1500), IFluidHandler.FluidAction.SIMULATE) == 1500 && tank.getContent() == 0,
				"simulating a fill should not change the tank");
		check(handler.fill(new FluidStack(Fluids.WATER, 1500), IFluidHandler.FluidAction.EXECUTE) == 1500, "the tank should accept water");
		check(tank.getContent() == 1500, "filling through the handler should add water, got " + tank.getContent());
		FluidStack drained = handler.drain(500, IFluidHandler.FluidAction.EXECUTE);
		check(drained.getFluid() == Fluids.WATER && drained.getAmount() == 500, "draining should return water");
		check(tank.getContent() == 1000, "draining through the handler should remove water, got " + tank.getContent());
		helper.succeed();
	}

}
