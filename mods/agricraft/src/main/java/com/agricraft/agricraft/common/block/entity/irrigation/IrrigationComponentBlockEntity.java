package com.agricraft.agricraft.common.block.entity.irrigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A block that holds water and levels it out with its horizontal neighbours.
 * <p>
 * Ported from AgriCraft 3's {@code TileEntityIrrigationComponent} (MIT). Water is stored as an integer amount in mB
 * plus a fluid level; each tick a component pushes water to lower neighbours in proportion to their surface factors.
 * InfinityLib's auto-synced fields are replaced by an update packet that is only sent once the level or content has
 * moved by more than the component's sync threshold.
 */
public abstract class IrrigationComponentBlockEntity extends BlockEntity {

	/** drain 1% each tick, so it takes 100 ticks (5 seconds) to fully drain a component */
	public static final float NETHER_DRAIN_FRACTION = 0.01F;

	private static final String TAG_CONTENT = "contents";
	private static final String TAG_LEVEL = "level";

	private final float minLevel;
	private final float maxLevel;

	// actual content and level, the level is relative to this block's y position
	private int contentBuffer;
	private float levelBuffer;
	private float prevLevel;
	// last values sent to clients
	private int syncedContent;
	private float syncedLevel;

	protected IrrigationComponentBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, float minLevel, float maxLevel) {
		super(type, pos, state);
		this.minLevel = minLevel;
		this.maxLevel = maxLevel;
		this.levelBuffer = minLevel;
		this.prevLevel = minLevel;
		this.syncedLevel = minLevel;
	}

	public void tick() {
		this.prevLevel = this.levelBuffer;
		if (this.getContent() > 0) {
			this.balanceWithNeighbours();
			this.runNetherLogic();
		}
		this.tickComponent();
	}

	protected abstract void tickComponent();

	/** Capacity (mB) of a single block of this component. */
	protected abstract int getBlockCapacity();

	protected abstract boolean canConnect(IrrigationComponentBlockEntity other);

	protected abstract boolean canTransfer(IrrigationComponentBlockEntity other, Direction dir);

	protected abstract int getLevelIntervalCount();

	protected abstract float getContentDeltaFraction();

	protected void balanceWithNeighbours() {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			IrrigationComponentBlockEntity component = this.getNeighbour(dir);
			if (component == null || !this.canTransfer(component, dir) || !component.canTransfer(this, dir.getOpposite())) {
				continue;
			}
			float yA = this.getFluidLevel();
			float yB = component.getFluidLevel();
			// only push to a neighbour with a lower fluid level
			if (yA > yB) {
				float fA = this.getSurfaceFactor();
				float fB = component.getSurfaceFactor();
				float v = (yA - Math.max(yB, this.getMinLevel())) * fA;
				float dv = fB * v / (fA + fB);
				if (dv > 0) {
					int transfer = (int) dv;
					transfer = Math.min(transfer, this.getContent());
					transfer = Math.min(transfer, component.getCapacity() - component.getContent());
					transfer = component.pushWater(transfer, true);
					this.drainWater(transfer, true);
				}
			}
		}
	}

	protected void runNetherLogic() {
		if (this.level == null || !this.level.dimensionType().ultraWarm()) {
			return;
		}
		this.drainWater(Math.max(1, (int) (this.getNetherEvaporationRate() * this.getCapacity())), true);
		if (this.level instanceof ServerLevel serverLevel && serverLevel.getRandom().nextDouble() <= 0.20) {
			double x = this.worldPosition.getX() + 0.5 + 0.5 * (serverLevel.getRandom().nextDouble() - 0.5);
			double y = this.getFluidLevel();
			double z = this.worldPosition.getZ() + 0.5 + 0.5 * (serverLevel.getRandom().nextDouble() - 0.5);
			serverLevel.sendParticles(ParticleTypes.CLOUD, x, y, z, 1, 0, 0.15, 0, 1);
			serverLevel.playSound(null, this.worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
					0.5F, 2.6F + (serverLevel.getRandom().nextFloat() - serverLevel.getRandom().nextFloat()) * 0.8F);
		}
	}

	protected float getNetherEvaporationRate() {
		return NETHER_DRAIN_FRACTION;
	}

	@Nullable
	public IrrigationComponentBlockEntity getNeighbour(Direction dir) {
		if (this.level == null || !dir.getAxis().isHorizontal()) {
			return null;
		}
		BlockPos pos = this.worldPosition.relative(dir);
		if (!this.level.isLoaded(pos)) {
			return null;
		}
		if (this.level.getBlockEntity(pos) instanceof IrrigationComponentBlockEntity component && !component.isRemoved() && this.canConnect(component)) {
			return component;
		}
		return null;
	}

	// ---- content and level ----

	public int getContent() {
		return this.contentBuffer;
	}

	/** Absolute fluid level (world y). */
	public float getFluidLevel() {
		return this.levelBuffer + this.worldPosition.getY();
	}

	public float getRenderLevel(float partialTicks) {
		return Mth.lerp(partialTicks, this.prevLevel, this.levelBuffer) + this.worldPosition.getY();
	}

	public int getCapacity() {
		return this.getBlockCapacity();
	}

	/** Absolute minimum fluid level (world y). */
	public float getMinLevel() {
		return this.minLevel + this.worldPosition.getY();
	}

	/** Absolute maximum fluid level (world y). */
	public float getMaxLevel() {
		return this.maxLevel + this.worldPosition.getY();
	}

	public float getSurfaceFactor() {
		return this.getCapacity() / (this.getMaxLevel() - this.getMinLevel());
	}

	public int pushWater(int max, boolean execute) {
		int fill = Math.max(0, Math.min(this.getCapacity() - this.getContent(), max));
		if (execute && fill > 0) {
			this.setContent(this.getContent() + fill);
		}
		return fill;
	}

	public int drainWater(int max, boolean execute) {
		int drain = Math.max(0, Math.min(this.getContent(), max));
		if (execute && drain > 0) {
			this.setContent(this.getContent() - drain);
		}
		return drain;
	}

	public void setContent(int content) {
		this.contentBuffer = Math.max(0, Math.min(this.getCapacity(), content));
		this.levelBuffer = this.calculateHeight(this.contentBuffer);
		this.onContentChanged();
	}

	/** Sets the absolute fluid level, the content is derived from it. */
	public void setFluidLevel(float level) {
		this.levelBuffer = Math.max(this.getMinLevel(), Math.min(this.getMaxLevel(), level)) - this.worldPosition.getY();
		this.contentBuffer = Math.max(0, Math.min(this.getCapacity(), this.calculateContents(this.levelBuffer)));
		this.onContentChanged();
	}

	/** Raw stored content of this block entity, without multi-block delegation. */
	protected int getStoredContent() {
		return this.contentBuffer;
	}

	/** Empties this block entity's own buffers, without multi-block delegation. */
	protected void resetStoredWater() {
		this.contentBuffer = 0;
		this.levelBuffer = this.minLevel;
		this.onContentChanged();
	}

	/** Raw stored level of this block entity (absolute), without multi-block delegation. */
	protected float getStoredLevel() {
		return this.levelBuffer + this.worldPosition.getY();
	}

	protected int calculateContents(float height) {
		return (int) (this.getCapacity() * (height + this.worldPosition.getY() - this.getMinLevel()) / (this.getMaxLevel() - this.getMinLevel()));
	}

	protected float calculateHeight(int contents) {
		return this.getMinLevel() - this.worldPosition.getY() + (contents + 0.0F) * (this.getMaxLevel() - this.getMinLevel()) / (this.getCapacity() + 0.0F);
	}

	private void onContentChanged() {
		if (this.level != null && !this.level.isClientSide) {
			this.level.blockEntityChanged(this.worldPosition);
			this.checkAndSyncIfNeeded();
		}
	}

	/** Syncs to the client if a difference greater than the sync threshold is detected, or the component ran dry or filled up. */
	protected void checkAndSyncIfNeeded() {
		boolean level = Math.abs(this.levelBuffer - this.syncedLevel) >= (this.getMaxLevel() - this.getMinLevel()) / this.getLevelIntervalCount();
		boolean content = Math.abs(this.contentBuffer - this.syncedContent) >= this.getContentDeltaFraction() * this.getCapacity();
		boolean edge = this.contentBuffer != this.syncedContent && (this.contentBuffer == 0 || this.contentBuffer == this.getCapacity());
		if (level || content || edge) {
			this.sync();
		}
	}

	protected void sync() {
		this.syncedContent = this.contentBuffer;
		this.syncedLevel = this.levelBuffer;
		if (this.level != null && !this.level.isClientSide) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	// ---- persistence and sync ----

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putInt(TAG_CONTENT, this.contentBuffer);
		tag.putFloat(TAG_LEVEL, this.levelBuffer);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		this.contentBuffer = tag.getInt(TAG_CONTENT);
		this.levelBuffer = tag.contains(TAG_LEVEL) ? tag.getFloat(TAG_LEVEL) : this.minLevel;
		this.syncedContent = this.contentBuffer;
		this.syncedLevel = this.levelBuffer;
	}

	@Override
	public CompoundTag getUpdateTag() {
		return this.saveWithoutMetadata();
	}

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/** Ticks on both sides, like AgriCraft 3: client-side balancing smooths the rendered level between syncs. */
	public static void ticker(Level level, BlockPos pos, BlockState state, IrrigationComponentBlockEntity component) {
		component.tick();
	}

}
