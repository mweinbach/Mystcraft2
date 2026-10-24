package com.agricraft.agricraft.common.util.fabric;

import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationTankBlockEntity;
import com.agricraft.agricraft.common.registry.ModBlockEntityTypes;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.level.material.Fluids;

/**
 * Exposes irrigation tanks to the Fabric Transfer API as water-only storage (the Fabric counterpart of AgriCraft 3's
 * Forge fluid capability). Amounts are converted between mB and droplets (81 droplets per mB).
 */
@SuppressWarnings("UnstableApiUsage")
public class FabricTankStorage extends SnapshotParticipant<Integer> implements SingleSlotStorage<FluidVariant> {

	private static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;

	private final IrrigationTankBlockEntity tank;

	public FabricTankStorage(IrrigationTankBlockEntity tank) {
		this.tank = tank;
	}

	public static void register() {
		FluidStorage.SIDED.registerForBlockEntity((tank, direction) -> new FabricTankStorage(tank), ModBlockEntityTypes.IRRIGATION_TANK.get());
	}

	private static boolean isWater(FluidVariant resource) {
		return resource.isOf(Fluids.WATER) && !resource.hasNbt();
	}

	@Override
	public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		if (!isWater(resource)) {
			return 0;
		}
		int inserted = this.tank.pushWater((int) Math.min(Integer.MAX_VALUE, maxAmount / DROPLETS_PER_MB), false);
		if (inserted > 0) {
			this.updateSnapshots(transaction);
			this.tank.pushWater(inserted, true);
		}
		return inserted * DROPLETS_PER_MB;
	}

	@Override
	public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		if (!isWater(resource)) {
			return 0;
		}
		int extracted = this.tank.drainWater((int) Math.min(Integer.MAX_VALUE, maxAmount / DROPLETS_PER_MB), false);
		if (extracted > 0) {
			this.updateSnapshots(transaction);
			this.tank.drainWater(extracted, true);
		}
		return extracted * DROPLETS_PER_MB;
	}

	@Override
	public boolean isResourceBlank() {
		return this.tank.getContent() <= 0;
	}

	@Override
	public FluidVariant getResource() {
		return this.isResourceBlank() ? FluidVariant.blank() : FluidVariant.of(Fluids.WATER);
	}

	@Override
	public long getAmount() {
		return this.tank.getContent() * DROPLETS_PER_MB;
	}

	@Override
	public long getCapacity() {
		return this.tank.getCapacity() * DROPLETS_PER_MB;
	}

	@Override
	protected Integer createSnapshot() {
		return this.tank.getContent();
	}

	@Override
	protected void readSnapshot(Integer snapshot) {
		this.tank.setContent(snapshot);
	}

}
