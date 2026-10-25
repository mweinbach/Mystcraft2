package com.agricraft.agricraft.common.block.entity.irrigation;

import com.agricraft.agricraft.api.config.IrrigationConfig;
import com.agricraft.agricraft.common.block.irrigation.IrrigationChannelBlock;
import com.agricraft.agricraft.common.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Irrigation channel (normal and hollow), ported from AgriCraft 3's {@code TileEntityIrrigationChannel} (MIT).
 * Channels connect to tanks and other channels; a closed valve stops the flow.
 */
public class IrrigationChannelBlockEntity extends IrrigationComponentBlockEntity {

	private static final float MIN_Y = 6 / 16.0F;
	private static final float MAX_Y = 10 / 16.0F;
	private static final int HEIGHT_INTERVALS = 6;
	private static final float CONTENT_DELTA_FRACTION = 0.10F;

	// client-side valve animation
	private ValveState valveState = ValveState.NONE;
	private IrrigationChannelBlock.Valve lastValve = null;
	private int counter = 0;

	public IrrigationChannelBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntityTypes.IRRIGATION_CHANNEL.get(), pos, state, MIN_Y, MAX_Y);
	}

	public IrrigationChannelBlock.Valve getValve() {
		BlockState state = this.getBlockState();
		return state.hasProperty(IrrigationChannelBlock.VALVE) ? state.getValue(IrrigationChannelBlock.VALVE) : IrrigationChannelBlock.Valve.NONE;
	}

	public boolean hasHandWheel() {
		return this.getBlockState().getBlock() instanceof IrrigationChannelBlock channel && channel.hasHandWheel();
	}

	public boolean hasValve() {
		return this.getValve().hasValve();
	}

	public boolean isOpen() {
		return this.getValve().canTransfer();
	}

	public boolean isClosed() {
		return this.getValve() == IrrigationChannelBlock.Valve.CLOSED;
	}

	public ValveState getValveState() {
		return this.valveState;
	}

	public float getValveAnimationProgress(float partialTicks) {
		return this.valveState.getAnimationProgress(this.counter, partialTicks);
	}

	@Override
	protected void tickComponent() {
		if (this.level == null || !this.level.isClientSide) {
			return;
		}
		// start the hand wheel animation whenever the valve changes, whoever changed it
		IrrigationChannelBlock.Valve valve = this.getValve();
		if (valve != this.lastValve) {
			if (this.lastValve == null || !this.lastValve.hasValve() || !valve.hasValve()) {
				this.setValveState(valve == IrrigationChannelBlock.Valve.OPEN ? ValveState.OPEN : valve == IrrigationChannelBlock.Valve.CLOSED ? ValveState.CLOSED : ValveState.NONE);
			} else {
				this.setValveState(valve.canTransfer() ? ValveState.OPENING : ValveState.CLOSING);
			}
			this.lastValve = valve;
		}
		if (this.counter > 0) {
			this.counter -= 1;
			if (this.counter <= 0) {
				this.valveState = this.valveState.getResult();
			}
		}
	}

	private void setValveState(ValveState state) {
		this.valveState = state;
		this.counter = state.getDuration();
	}

	@Override
	protected int getBlockCapacity() {
		return IrrigationConfig.channelCapacity;
	}

	@Override
	protected boolean canConnect(IrrigationComponentBlockEntity other) {
		return true;
	}

	@Override
	protected boolean canTransfer(IrrigationComponentBlockEntity other, Direction dir) {
		return this.canConnect(other) && this.getValve().canTransfer();
	}

	@Override
	protected int getLevelIntervalCount() {
		return HEIGHT_INTERVALS;
	}

	@Override
	protected float getContentDeltaFraction() {
		return CONTENT_DELTA_FRACTION;
	}

	public enum ValveState {
		NONE(0),
		OPEN(1),
		CLOSED(0),
		OPENING(CLOSED, OPEN, 20),
		CLOSING(OPEN, CLOSED, 20);

		private final float target;
		private final ValveState from;
		private final ValveState to;
		private final int duration;

		ValveState(float target) {
			this.target = target;
			this.from = this;
			this.to = this;
			this.duration = -1;
		}

		ValveState(ValveState from, ValveState to, int duration) {
			this.from = from;
			this.to = to;
			this.duration = duration;
			this.target = to.target;
		}

		public float getAnimationProgress(int counter, float partialTick) {
			if (this.hasAnimation()) {
				// to and from are inverted as the counter is counting down
				return Mth.lerp(Math.min(1.0F, (counter + partialTick) / this.getDuration()), this.to.target, this.from.target);
			}
			return this.target;
		}

		public ValveState getResult() {
			return this.to;
		}

		public boolean hasAnimation() {
			return this.getDuration() > 0;
		}

		public int getDuration() {
			return this.duration;
		}
	}

}
