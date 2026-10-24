package com.agricraft.agricraft.common.block.entity.irrigation;

import com.agricraft.agricraft.api.config.IrrigationConfig;
import com.agricraft.agricraft.common.block.irrigation.SprinklerBlock;
import com.agricraft.agricraft.common.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * Sprinkler, ported from AgriCraft 3's {@code TileEntitySprinkler} (MIT).
 * <p>
 * Pulls water from the channel above and, while it has water, sweeps a 7x7 area one column per tick: plants get a
 * chance at a growth tick and farmland is moistened.
 */
public class SprinklerBlockEntity extends BlockEntity {

	private static final int WORK_HEIGHT = 5;
	private static final int WORK_RADIUS = 3;
	private static final int WORK_DIAMETER = 1 + 2 * WORK_RADIUS;
	private static final int WORK_AREA = WORK_DIAMETER * WORK_DIAMETER;
	private static final int WORK_COOLDOWN = 20;
	private static final int ROTATION_SPEED = 360 / (20 * 2); // one full rotation every 2 seconds

	/** Particle setting of the client (0 = all, 1 = decreased, 2 = minimal); installed by the client initialiser. */
	public static IntSupplier PARTICLE_SETTING = () -> 0;

	private int buffer;
	private int columnCounter;
	private int remainingWater;
	private int remainingTicks;

	// animation data
	private int angle;
	private int prevAngle;
	private int particleCounter;

	public SprinklerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntityTypes.SPRINKLER.get(), pos, state);
	}

	public boolean isActive() {
		BlockState state = this.getBlockState();
		return state.hasProperty(SprinklerBlock.ACTIVE) && state.getValue(SprinklerBlock.ACTIVE);
	}

	public static void ticker(Level level, BlockPos pos, BlockState state, SprinklerBlockEntity sprinkler) {
		sprinkler.tick();
	}

	public void tick() {
		if (this.level == null) {
			return;
		}
		if (!this.level.isClientSide) {
			this.pullWater();
			this.runSprinklerLogic();
		}
		this.prevAngle = this.angle;
		if (this.isActive()) {
			this.angle = (this.angle + ROTATION_SPEED) % 360;
			if (this.level.isClientSide) {
				this.spawnSprinklerParticles();
			}
		}
	}

	public float getAngle(float partialTick) {
		// unwrap so the interpolation does not spin backwards at the 360 -> 0 wrap
		float current = this.angle < this.prevAngle ? this.angle + 360 : this.angle;
		return Mth.lerp(partialTick, this.prevAngle, current);
	}

	public int getBuffer() {
		return this.buffer;
	}

	@Nullable
	public IrrigationChannelBlockEntity getChannel() {
		if (this.level == null) {
			return null;
		}
		return this.level.getBlockEntity(this.worldPosition.above()) instanceof IrrigationChannelBlockEntity channel ? channel : null;
	}

	protected void pullWater() {
		IrrigationChannelBlockEntity channel = this.getChannel();
		if (channel == null) {
			return;
		}
		int pulled = channel.drainWater(Math.max(this.getBufferSize() - this.buffer, 0), true);
		if (pulled > 0) {
			this.buffer += pulled;
			this.setChanged();
		}
	}

	protected void runSprinklerLogic() {
		// Step 1: check if we need to refresh (or reset) the water usage variables
		if (this.remainingTicks <= 0 || this.remainingWater < 0) {
			this.remainingWater = Math.abs(IrrigationConfig.sprinklerWaterConsumption);
			this.remainingTicks = WORK_COOLDOWN;
		}
		// Step 2: calculate the per-tick water usage rate (int division), then decrease the remainder-tracking variables
		final int waterUsageThisTick = this.remainingWater / this.remainingTicks;
		this.remainingWater -= waterUsageThisTick;
		this.remainingTicks -= 1;
		// Step 3: check if there is enough water to irrigate this tick, and also if this is a change in state
		final boolean active = this.buffer >= waterUsageThisTick && this.buffer > 0;
		if (active != this.isActive() && this.level != null) {
			this.level.setBlock(this.worldPosition, this.getBlockState().setValue(SprinklerBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
		}
		// Step 4: if we aren't irrigating, stop
		if (!active) {
			return;
		}
		// Step 5: otherwise, consume the water and continue
		this.buffer -= waterUsageThisTick;
		// Step 6: if we're within bounds, sprinkle the next column
		if (this.columnCounter >= 0 && this.columnCounter < WORK_AREA) {
			this.irrigateCurrentColumn();
		}
		// Step 7: update the counter
		this.columnCounter += 1;
		// Step 8: if the counter exceeds both minima, or if it is (incorrectly) negative, reset it
		if (this.columnCounter >= WORK_AREA && this.columnCounter >= IrrigationConfig.sprinkleInterval || this.columnCounter < 0) {
			this.columnCounter = 0;
		}
		this.setChanged();
	}

	protected void irrigateCurrentColumn() {
		final int targetX = this.worldPosition.getX() - WORK_RADIUS + (this.columnCounter % WORK_DIAMETER);
		final int targetZ = this.worldPosition.getZ() - WORK_RADIUS + (this.columnCounter / WORK_DIAMETER);
		final int startY = this.worldPosition.getY() - 1;
		final int stopY = this.level == null ? startY : Math.max(this.worldPosition.getY() - WORK_HEIGHT, this.level.getMinBuildHeight());
		this.irrigateColumn(targetX, targetZ, startY, stopY);
	}

	/**
	 * Searches a vertical column of positions from the top. Air is skipped; plants get an independent chance at a
	 * growth tick and the search continues below them; farmland is moistened and ends the search; anything else
	 * blocks the sprinkler. A plant at the lowest position is too far away to be helped.
	 */
	protected void irrigateColumn(final int targetX, final int targetZ, final int highestY, final int lowestY) {
		if (!(this.level instanceof ServerLevel serverLevel)) {
			return;
		}
		BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
		for (int targetY = highestY; targetY >= lowestY; targetY -= 1) {
			target.set(targetX, targetY, targetZ);
			BlockState state = serverLevel.getBlockState(target);
			Block block = state.getBlock();
			// Option A: skip empty/air blocks
			if (state.isAir()) {
				continue;
			}
			// Option B: give plants a chance to grow, and then continue onward to irrigate the farmland too
			if (isPlant(block) && targetY != lowestY) {
				if (state.isRandomlyTicking() && serverLevel.getRandom().nextDouble() < IrrigationConfig.sprinkleGrowthChance) {
					state.randomTick(serverLevel, target.immutable(), serverLevel.getRandom());
				}
				continue;
			}
			// Option C: dry farmland gets set as moist
			if (block instanceof FarmBlock) {
				if (state.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) {
					serverLevel.setBlock(target, state.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS);
				}
				break;
			}
			// Option D: anything else blocks the sprinkler's irrigation
			break;
		}
	}

	private static boolean isPlant(Block block) {
		// AgriCraft 3 used Forge's IPlantable / IGrowable; the loader-neutral equivalents are bushes and bonemealables
		return block instanceof BushBlock || block instanceof BonemealableBlock;
	}

	protected int getBufferSize() {
		return 2 * IrrigationConfig.sprinklerWaterConsumption;
	}

	protected void spawnSprinklerParticles() {
		if (this.level == null) {
			return;
		}
		boolean vapour = this.level.dimensionType().ultraWarm();
		int particleSetting = PARTICLE_SETTING.getAsInt(); // 0 = all, 1 = decreased, 2 = minimal
		particleSetting += vapour ? 6 : 2;
		this.particleCounter = (this.particleCounter + 1) % particleSetting;
		if (this.particleCounter != 0) {
			return;
		}
		double x = this.worldPosition.getX() + 0.5;
		double y = this.worldPosition.getY() + 0.35;
		double z = this.worldPosition.getZ() + 0.5;
		for (int i = 0; i < 4; i++) {
			float alpha = -(this.angle + 90 * i) * ((float) Math.PI) / 180;
			float cosA = Mth.cos(alpha);
			float sinA = Mth.sin(alpha);
			double xOffset = (4 / 16.0) * cosA;
			double zOffset = (4 / 16.0) * sinA;
			float radius = 0.35F;
			if (vapour) {
				this.level.addParticle(ParticleTypes.CLOUD, x + xOffset, y, z + zOffset, 0.15 * cosA, 0.25, 0.15 * sinA);
			} else {
				for (int j = 0; j <= 4; j++) {
					float beta = -j * ((float) Math.PI) / (8.0F);
					this.level.addParticle(ParticleTypes.SPLASH,
							x + xOffset * (4 - j) / 4, y, z + zOffset * (4 - j) / 4,
							radius * cosA, radius * Mth.sin(beta), radius * sinA);
				}
			}
		}
		if (vapour) {
			this.level.playLocalSound(x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
					0.5F, 2.6F + (this.level.getRandom().nextFloat() - this.level.getRandom().nextFloat()) * 0.8F, false);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putInt("contents", this.buffer);
		tag.putInt("column", this.columnCounter);
		tag.putInt("remaining_water", this.remainingWater);
		tag.putInt("remaining_ticks", this.remainingTicks);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		this.buffer = tag.getInt("contents");
		this.columnCounter = tag.getInt("column");
		this.remainingWater = tag.getInt("remaining_water");
		this.remainingTicks = tag.getInt("remaining_ticks");
	}

}
