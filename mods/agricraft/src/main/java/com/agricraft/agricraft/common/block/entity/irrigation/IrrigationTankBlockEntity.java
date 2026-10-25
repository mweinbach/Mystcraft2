package com.agricraft.agricraft.common.block.entity.irrigation;

import com.agricraft.agricraft.api.config.IrrigationConfig;
import com.agricraft.agricraft.common.block.irrigation.IrrigationChannelBlock;
import com.agricraft.agricraft.common.block.irrigation.IrrigationTankBlock;
import com.agricraft.agricraft.common.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Irrigation tank, ported from AgriCraft 3's {@code TileEntityIrrigationTank} (MIT).
 * <p>
 * Tanks placed in a full cuboid merge into one multi-block. The block at the minimum corner (the origin) stores the
 * water for the whole structure and every other block delegates to it.
 */
public class IrrigationTankBlockEntity extends IrrigationComponentBlockEntity {

	private static final float MIN_Y = 2 / 16.0F;
	private static final float MAX_Y = 1;
	private static final int HEIGHT_INTERVALS = 16;
	private static final float CONTENT_DELTA_FRACTION = 0.05F;

	private BlockPos min;
	private BlockPos max;

	public IrrigationTankBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntityTypes.IRRIGATION_TANK.get(), pos, state, MIN_Y, MAX_Y);
		this.min = pos;
		this.max = pos;
	}

	public BlockPos getMultiBlockMin() {
		return this.min;
	}

	public BlockPos getMultiBlockMax() {
		return this.max;
	}

	public boolean isMultiBlockOrigin() {
		return this.worldPosition.equals(this.min);
	}

	public boolean isMultiBlock() {
		return !this.min.equals(this.max);
	}

	public boolean isInMultiBlock(BlockPos pos) {
		return pos.getX() >= this.min.getX() && pos.getY() >= this.min.getY() && pos.getZ() >= this.min.getZ()
				&& pos.getX() <= this.max.getX() && pos.getY() <= this.max.getY() && pos.getZ() <= this.max.getZ();
	}

	/**
	 * @return the block entity that holds the water of this multi-block, or null if it is not available (e.g. not loaded)
	 */
	@Nullable
	public IrrigationTankBlockEntity getMultiBlockOrigin() {
		if (this.isMultiBlockOrigin()) {
			return this;
		}
		if (this.level == null || !this.level.isLoaded(this.min)) {
			return null;
		}
		if (this.level.getBlockEntity(this.min) instanceof IrrigationTankBlockEntity origin
				&& !origin.isRemoved() && origin.isMultiBlockOrigin() && origin.max.equals(this.max)) {
			return origin;
		}
		return null;
	}

	@Override
	protected void tickComponent() {
		// a block whose origin vanished (e.g. it was replaced without a block update) falls back to a single tank
		if (this.level != null && !this.level.isClientSide && !this.isMultiBlockOrigin() && this.level.isLoaded(this.min)
				&& this.getMultiBlockOrigin() == null) {
			this.resetToSingle(this.getStoredLevel());
		}
	}

	@Override
	protected int getBlockCapacity() {
		return IrrigationConfig.tankCapacity;
	}

	@Override
	protected void runNetherLogic() {
		if (this.worldPosition.getY() == (int) this.getFluidLevel()) {
			super.runNetherLogic();
		} else if (this.worldPosition.getY() == this.max.getY() && this.getContent() == this.getCapacity()) {
			// for the first tick when the tank is completely full
			super.runNetherLogic();
		}
	}

	@Override
	protected float getNetherEvaporationRate() {
		return (super.getNetherEvaporationRate() * this.getBlockCapacity()) / this.getCapacity();
	}

	@Override
	public int getContent() {
		if (this.isMultiBlockOrigin()) {
			return super.getContent();
		}
		IrrigationTankBlockEntity origin = this.getMultiBlockOrigin();
		return origin == null ? 0 : origin.getContent();
	}

	@Override
	public float getFluidLevel() {
		if (this.isMultiBlockOrigin()) {
			return super.getFluidLevel();
		}
		IrrigationTankBlockEntity origin = this.getMultiBlockOrigin();
		return origin == null ? this.getMinLevel() : origin.getFluidLevel();
	}

	@Override
	public float getRenderLevel(float partialTicks) {
		if (this.isMultiBlockOrigin()) {
			float level = super.getRenderLevel(partialTicks);
			if (level == this.getMaxLevel()) {
				level = level - 0.0001F;
			}
			return level;
		}
		IrrigationTankBlockEntity origin = this.getMultiBlockOrigin();
		return origin == null ? this.getMinLevel() : origin.getRenderLevel(partialTicks);
	}

	@Override
	public int pushWater(int max, boolean execute) {
		if (this.isMultiBlockOrigin()) {
			return super.pushWater(max, execute);
		}
		IrrigationTankBlockEntity origin = this.getMultiBlockOrigin();
		return origin == null ? 0 : origin.pushWater(max, execute);
	}

	@Override
	public int drainWater(int max, boolean execute) {
		if (this.isMultiBlockOrigin()) {
			return super.drainWater(max, execute);
		}
		IrrigationTankBlockEntity origin = this.getMultiBlockOrigin();
		return origin == null ? 0 : origin.drainWater(max, execute);
	}

	@Override
	public void setContent(int content) {
		if (this.isMultiBlockOrigin()) {
			super.setContent(content);
		} else {
			IrrigationTankBlockEntity origin = this.getMultiBlockOrigin();
			if (origin != null) {
				origin.setContent(content);
			}
		}
	}

	@Override
	public void setFluidLevel(float level) {
		if (this.isMultiBlockOrigin()) {
			super.setFluidLevel(level);
		} else {
			IrrigationTankBlockEntity origin = this.getMultiBlockOrigin();
			if (origin != null) {
				origin.setFluidLevel(level);
			}
		}
	}

	@Override
	public int getCapacity() {
		return this.getBlockCapacity()
				* (this.max.getX() - this.min.getX() + 1)
				* (this.max.getY() - this.min.getY() + 1)
				* (this.max.getZ() - this.min.getZ() + 1);
	}

	@Override
	public float getMinLevel() {
		return MIN_Y + this.min.getY();
	}

	@Override
	public float getMaxLevel() {
		return MAX_Y + this.max.getY();
	}

	@Override
	protected boolean canConnect(IrrigationComponentBlockEntity other) {
		return other instanceof IrrigationChannelBlockEntity;
	}

	@Override
	protected boolean canTransfer(IrrigationComponentBlockEntity other, Direction dir) {
		return this.canConnect(other);
	}

	@Override
	protected int getLevelIntervalCount() {
		return HEIGHT_INTERVALS;
	}

	@Override
	protected float getContentDeltaFraction() {
		return CONTENT_DELTA_FRACTION;
	}

	// ---- multi-block forming ----

	/**
	 * Tries to merge this tank with the tanks it touches. The structure must be a completely filled cuboid of tanks; if
	 * any position inside the bounding box is not a tank, nothing changes. Existing multi-blocks that touch this tank
	 * are absorbed and their water is pooled.
	 */
	public void checkAndFormMultiBlock() {
		if (this.level == null || this.level.isClientSide) {
			return;
		}
		Level level = this.level;
		BlockPos.MutableBlockPos lo = this.worldPosition.mutable();
		BlockPos.MutableBlockPos hi = this.worldPosition.mutable();
		Set<BlockPos> checked = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(this.worldPosition);
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			if (checked.contains(pos)) {
				continue;
			}
			if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof IrrigationTankBlockEntity tank)) {
				// a position inside the bounding box is not a tank: no valid cuboid
				return;
			}
			checked.add(pos);
			// expand the bounding box to cover the multi-block this tank is already part of
			for (BlockPos corner : new BlockPos[]{tank.min, tank.max}) {
				int x1 = Math.min(lo.getX(), corner.getX()), y1 = Math.min(lo.getY(), corner.getY()), z1 = Math.min(lo.getZ(), corner.getZ());
				int x2 = Math.max(hi.getX(), corner.getX()), y2 = Math.max(hi.getY(), corner.getY()), z2 = Math.max(hi.getZ(), corner.getZ());
				if (x1 != lo.getX() || y1 != lo.getY() || z1 != lo.getZ() || x2 != hi.getX() || y2 != hi.getY() || z2 != hi.getZ()) {
					lo.set(x1, y1, z1);
					hi.set(x2, y2, z2);
					// every position in the new bounding box must be checked
					BlockPos.betweenClosed(lo, hi).forEach(p -> {
						if (!checked.contains(p)) {
							queue.add(p.immutable());
						}
					});
				}
			}
			// continue with touching tanks
			for (Direction dir : Direction.values()) {
				BlockPos next = pos.relative(dir);
				if (!checked.contains(next) && level.isLoaded(next) && level.getBlockEntity(next) instanceof IrrigationTankBlockEntity) {
					queue.add(next);
				}
			}
		}
		BlockPos newMin = lo.immutable();
		BlockPos newMax = hi.immutable();
		if (newMin.equals(newMax)) {
			return;
		}
		// pool the water: only origins hold water, every other block stores zero
		int total = 0;
		List<IrrigationTankBlockEntity> tanks = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(newMin, newMax)) {
			if (level.getBlockEntity(pos) instanceof IrrigationTankBlockEntity tank) {
				tanks.add(tank);
				total += tank.getStoredContent();
			}
		}
		// assign the bounds to every member first, so block updates below see a consistent structure
		for (IrrigationTankBlockEntity tank : tanks) {
			tank.resetStoredWater();
			tank.min = newMin;
			tank.max = newMax;
		}
		IrrigationTankBlockEntity origin = null;
		for (IrrigationTankBlockEntity tank : tanks) {
			if (tank.isMultiBlockOrigin()) {
				origin = tank;
			}
			tank.setChanged();
			tank.updateMultiBlockState();
			tank.sync();
		}
		if (origin != null) {
			origin.setContent(total);
			origin.sync();
		}
	}

	/**
	 * Splits the multi-block into single tanks and hands each one its part of the stored water.
	 * <p>
	 * The water is shared out by amount, not by level: it fills the structure from the bottom layer up, each layer
	 * holding one tank's capacity per position, and every position takes an even part of its layer. Converting the
	 * structure's level instead made or destroyed water, because the multi-block's height excludes one tank floor and
	 * each single tank excludes its own. The part that belongs to the excluded (removed) position leaves with it.
	 * <p>
	 * Members in unloaded chunks are loaded first, so their part is not lost: they store no water of their own and
	 * would otherwise come back later as empty tanks.
	 *
	 * @param excluded a position that is being removed and must not be touched (may be null)
	 */
	public void unFormMultiBlock(@Nullable BlockPos excluded) {
		if (this.level == null || this.level.isClientSide || !this.isMultiBlock()) {
			return;
		}
		Level level = this.level;
		BlockPos oldMin = this.min;
		BlockPos oldMax = this.max;
		// Level#getBlockEntity loads the chunk on the server thread, which brings unloaded members (and the origin) in
		int total = level.getBlockEntity(oldMin) instanceof IrrigationTankBlockEntity origin
				&& origin.min.equals(oldMin) && origin.max.equals(oldMax) ? origin.getStoredContent() : 0;
		Map<BlockPos, IrrigationTankBlockEntity> tanks = new HashMap<>();
		for (BlockPos pos : BlockPos.betweenClosed(oldMin, oldMax)) {
			if (pos.equals(excluded)) {
				continue;
			}
			if (level.getBlockEntity(pos) instanceof IrrigationTankBlockEntity tank && tank.min.equals(oldMin) && tank.max.equals(oldMax)) {
				tanks.put(pos.immutable(), tank);
			}
		}
		// detach every member first, so block updates below do not trigger nested un-forming
		for (IrrigationTankBlockEntity tank : tanks.values()) {
			tank.min = tank.worldPosition;
			tank.max = tank.worldPosition;
		}
		int width = (oldMax.getX() - oldMin.getX() + 1) * (oldMax.getZ() - oldMin.getZ() + 1);
		int remaining = total;
		for (int y = oldMin.getY(); y <= oldMax.getY(); y++) {
			int layer = Math.min(remaining, this.getBlockCapacity() * width);
			remaining -= layer;
			int index = 0;
			for (int x = oldMin.getX(); x <= oldMax.getX(); x++) {
				for (int z = oldMin.getZ(); z <= oldMax.getZ(); z++) {
					int part = layer / width + (index < layer % width ? 1 : 0);
					index++;
					IrrigationTankBlockEntity tank = tanks.get(new BlockPos(x, y, z));
					if (tank != null) {
						tank.applySingleContent(part);
					}
				}
			}
		}
	}

	private void resetToSingle(float level) {
		this.min = this.worldPosition;
		this.max = this.worldPosition;
		this.resetStoredWater();
		super.setFluidLevel(level);
		this.setChanged();
		this.updateMultiBlockState();
		this.sync();
	}

	private void applySingleContent(int content) {
		this.resetStoredWater();
		super.setContent(content);
		this.setChanged();
		this.updateMultiBlockState();
		this.sync();
	}

	/** Called when a neighbouring block changed. */
	public void onNeighbourChanged(BlockPos fromPos) {
		if (this.level == null || this.level.isClientSide || !this.isMultiBlock() || !this.isInMultiBlock(fromPos)) {
			return;
		}
		if (this.level.getBlockEntity(fromPos) instanceof IrrigationTankBlockEntity tank && tank.min.equals(this.min) && tank.max.equals(this.max)) {
			return;
		}
		this.unFormMultiBlock(fromPos);
	}

	/** Recomputes the connection properties of this block from its position in the multi-block. */
	private void updateMultiBlockState() {
		if (this.level == null) {
			return;
		}
		BlockState state = this.getBlockState();
		if (!(state.getBlock() instanceof IrrigationTankBlock)) {
			return;
		}
		BlockState updated = state.setValue(IrrigationTankBlock.DOWN, this.worldPosition.getY() > this.min.getY());
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos neighbour = this.worldPosition.relative(dir);
			IrrigationTankBlock.Connection connection;
			if (this.isInMultiBlock(neighbour)) {
				connection = IrrigationTankBlock.Connection.TANK;
			} else if (this.level.getBlockState(neighbour).getBlock() instanceof IrrigationChannelBlock) {
				connection = IrrigationTankBlock.Connection.CHANNEL;
			} else {
				connection = IrrigationTankBlock.Connection.NONE;
			}
			updated = updated.setValue(IrrigationTankBlock.getConnection(dir), connection);
		}
		if (updated != state) {
			this.level.setBlock(this.worldPosition, updated, Block.UPDATE_ALL);
		}
	}

	// ---- persistence ----

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.put("min", NbtUtils.writeBlockPos(this.min));
		tag.put("max", NbtUtils.writeBlockPos(this.max));
	}

	@Override
	public void load(CompoundTag tag) {
		this.min = tag.contains("min") ? NbtUtils.readBlockPos(tag.getCompound("min")) : this.worldPosition;
		this.max = tag.contains("max") ? NbtUtils.readBlockPos(tag.getCompound("max")) : this.worldPosition;
		super.load(tag);
	}

	@Nullable
	public static IrrigationTankBlockEntity get(Level level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		return blockEntity instanceof IrrigationTankBlockEntity tank ? tank : null;
	}

}
