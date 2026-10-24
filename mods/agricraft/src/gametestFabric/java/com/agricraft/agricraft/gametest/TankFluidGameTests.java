package com.agricraft.agricraft.gametest;

import com.agricraft.agricraft.api.config.IrrigationConfig;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationTankBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;

import static com.agricraft.agricraft.gametest.GameTestSupport.TEMPLATE;
import static com.agricraft.agricraft.gametest.GameTestSupport.check;

/** The irrigation tank through Fabric Transfer; the Forge twin uses the fluid capability. */
public class TankFluidGameTests {

	private static final long MB = FluidConstants.BUCKET / 1000;

	@GameTest(template = TEMPLATE)
	public static void tankExposesItsWaterToPipes(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		IrrigationTankBlockEntity tank = IrrigationGameTests.tank(helper, pos);
		Storage<FluidVariant> storage = FluidStorage.SIDED.find(helper.getLevel(), helper.absolutePos(pos), Direction.UP);
		check(storage != null, "the tank should expose a fluid storage");
		long capacity = 0;
		for (StorageView<FluidVariant> view : storage) {
			capacity += view.getCapacity();
		}
		check(capacity == IrrigationConfig.tankCapacity * MB, "the storage should report the tank capacity");
		try (Transaction transaction = Transaction.openOuter()) {
			check(storage.insert(FluidVariant.of(Fluids.LAVA), 1000 * MB, transaction) == 0, "a tank should refuse lava");
		}
		try (Transaction transaction = Transaction.openOuter()) {
			check(storage.insert(FluidVariant.of(Fluids.WATER), 1500 * MB, transaction) == 1500 * MB, "the tank should accept water");
			// aborted: nothing changes
		}
		check(tank.getContent() == 0, "an aborted transaction should not change the tank, got " + tank.getContent());
		try (Transaction transaction = Transaction.openOuter()) {
			check(storage.insert(FluidVariant.of(Fluids.WATER), 1500 * MB, transaction) == 1500 * MB, "the tank should accept water");
			transaction.commit();
		}
		check(tank.getContent() == 1500, "filling through the storage should add water, got " + tank.getContent());
		try (Transaction transaction = Transaction.openOuter()) {
			check(storage.extract(FluidVariant.of(Fluids.WATER), 500 * MB, transaction) == 500 * MB, "draining should return water");
			transaction.commit();
		}
		check(tank.getContent() == 1000, "draining through the storage should remove water, got " + tank.getContent());
		helper.succeed();
	}

}
