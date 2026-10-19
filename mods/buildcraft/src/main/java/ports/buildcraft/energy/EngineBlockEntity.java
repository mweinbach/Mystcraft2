package ports.buildcraft.energy;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.core.Wrenches;
import ports.buildcraft.lib.Power;
import ports.buildcraft.lib.SyncedBlockEntity;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.platform.PortHolder;

/**
 * Upstream {@code TileEngineBase_BC8}: heat, power stages, piston strokes and power delivery. Power
 * is kept in micro-MJ; the receiving block sees whole loader energy units at {@link Power#UNITS_PER_MJ}
 * per MJ, and sub-unit remainders stay in the engine.
 */
public abstract class EngineBlockEntity extends SyncedBlockEntity implements PortHolder {
    public static final double MIN_HEAT = 20;
    public static final double IDEAL_HEAT = 100;
    public static final double MAX_HEAT = 250;
    public static final double HEAT_PER_MJ = 0.0023;
    private static final long REQUEST_UNITS = Integer.MAX_VALUE;

    protected double heat = MIN_HEAT;
    protected long power;
    protected long currentOutput;
    protected boolean isRedstonePowered;
    private float progress;
    private float lastProgress;
    private int progressPart;
    private boolean isPumping;
    private PowerStage powerStage = PowerStage.BLUE;

    protected EngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(EngineBlock.FACING);
    }

    // Engine properties, per subclass.
    public abstract long getMaxPower();
    public abstract long maxPowerExtracted();
    public abstract long getCurrentOutput();
    public abstract boolean isBurning();
    protected abstract boolean isRedstoneOnly();

    protected int getMaxChainLength() {
        return 2;
    }

    public boolean isActive() {
        return true;
    }

    protected void burn() {}

    public boolean hasMenu() {
        return false;
    }

    public InteractionResult onActivated(ServerPlayer player, InteractionHand hand, Direction side) {
        return InteractionResult.PASS;
    }

    public NonNullList<ItemStack> drops() {
        return NonNullList.create();
    }

    // Placement and rotation.

    void onPlaced() {
        onNeighbourChanged();
        rotateIfInvalid(null);
    }

    void onNeighbourChanged() {
        if (level != null) isRedstonePowered = level.hasNeighborSignal(worldPosition);
    }

    private void rotateIfInvalid(@Nullable Direction current) {
        if (current != null && receiverToPower(current) != null) return;
        if (!attemptRotation(current) && current == null) setFacing(Direction.UP);
    }

    /** Wrench rotation: advance to the next facing with a receiver, as upstream {@code attemptRotation}. */
    public boolean attemptRotation() {
        return attemptRotation(facing());
    }

    private boolean attemptRotation(@Nullable Direction from) {
        Direction current = from;
        for (int i = 0; i < 6; i++) {
            current = Wrenches.nextFacing(current);
            if (receiverToPower(current) != null) {
                if (from != current) {
                    setFacing(current);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private void setFacing(Direction direction) {
        if (level == null || facing() == direction) return;
        level.setBlock(worldPosition, getBlockState().setValue(EngineBlock.FACING, direction), 3);
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    // Heat and stages.

    public double getPowerLevel() {
        return power / (double) getMaxPower();
    }

    public double getHeatLevel() {
        return (heat - MIN_HEAT) / (MAX_HEAT - MIN_HEAT);
    }

    public double getHeat() {
        return heat;
    }

    public long getEnergyStored() {
        return power;
    }

    public void updateHeatLevel() {
        heat = ((MAX_HEAT - MIN_HEAT) * getPowerLevel()) + MIN_HEAT;
    }

    protected PowerStage computePowerStage() {
        double heatLevel = getHeatLevel();
        if (heatLevel < 0.25f) return PowerStage.BLUE;
        else if (heatLevel < 0.5f) return PowerStage.GREEN;
        else if (heatLevel < 0.75f) return PowerStage.YELLOW;
        else if (heatLevel < 0.85f) return PowerStage.RED;
        else return PowerStage.OVERHEAT;
    }

    public final PowerStage getPowerStage() {
        if (level != null && !level.isClientSide) {
            PowerStage newStage = computePowerStage();
            if (powerStage != newStage) {
                powerStage = newStage;
                if (getBlockState().getValue(EngineBlock.STAGE) != newStage) {
                    level.setBlock(worldPosition, getBlockState().setValue(EngineBlock.STAGE, newStage), 2);
                }
            }
            return powerStage;
        }
        return getBlockState().getValue(EngineBlock.STAGE);
    }

    public double getPistonSpeed() {
        return switch (getPowerStage()) {
            case BLUE -> 0.02;
            case GREEN -> 0.04;
            case YELLOW -> 0.08;
            case RED -> 0.12;
            default -> 0;
        };
    }

    // Ticking.

    void clientTick() {
        lastProgress = progress;
        if (isPumping) {
            progress += (float) getPistonSpeed();
            if (progress >= 1) progress = 0;
        } else if (progress > 0) {
            progress -= 0.01f;
        }
    }

    void serverTick() {
        boolean overheat = getPowerStage() == PowerStage.OVERHEAT;
        if (!isRedstonePowered) {
            if (power > Power.MJ) power -= Power.MJ;
            else if (power > 0) power = 0;
        }
        updateHeatLevel();
        getPowerStage();
        engineUpdate();

        EnergyPort receiver = receiverToPower(facing());
        boolean pulsedPower = receiver != null && receiver.acceptsRedstoneEngine();
        if (progressPart != 0) {
            progress += (float) getPistonSpeed();
            if (progress > 0.5 && progressPart == 1) {
                progressPart = 2;
                if (pulsedPower) sendPower(receiver);
            } else if (progress >= 1) {
                progress = 0;
                progressPart = 0;
            }
        } else if (isRedstonePowered && isActive()) {
            if (getPowerToExtract(receiver, false) > 0) {
                progressPart = 1;
                setPumping(true);
            } else {
                setPumping(false);
            }
        } else {
            setPumping(false);
        }
        if (!pulsedPower) {
            if (isRedstonePowered && isActive()) sendPower(receiver);
            else currentOutput = 0;
        }
        if (!overheat) burn();
        setChanged();
        flushSync();
    }

    protected void engineUpdate() {
        if (!isRedstonePowered) {
            if (power >= 1) power -= 1;
            else power = 0;
        }
    }

    private void setPumping(boolean pumping) {
        if (isPumping == pumping) return;
        isPumping = pumping;
        queueSync();
    }

    public boolean isPumping() {
        return isPumping;
    }

    private long getPowerToExtract(@Nullable EnergyPort receiver, boolean doExtract) {
        if (receiver == null) return 0;
        return extractPower(0, powerRequested(receiver), doExtract);
    }

    private void sendPower(@Nullable EnergyPort receiver) {
        if (receiver == null) return;
        long extracted = getPowerToExtract(receiver, false);
        if (extracted > 0) {
            long accepted = Power.fromUnits(receiver.receive(Power.toUnits(extracted), false));
            extractPower(accepted, accepted, true);
        }
    }

    private static long powerRequested(EnergyPort receiver) {
        return Power.fromUnits(receiver.receive(REQUEST_UNITS, true));
    }

    public void addPower(long microJoules) {
        power += microJoules;
        if (power > getMaxPower()) power = getMaxPower();
    }

    public long extractPower(long min, long max, boolean doExtract) {
        if (power < min) return 0;
        long actualMax = Math.min(max, maxPowerExtracted());
        if (actualMax < min) return 0;
        long extracted;
        if (power >= actualMax) {
            extracted = actualMax;
            if (doExtract) power -= actualMax;
        } else {
            extracted = power;
            if (doExtract) power = 0;
        }
        return extracted;
    }

    /**
     * Upstream {@code getReceiverToPower}: walks through up to {@link #getMaxChainLength()} engines of
     * the same kind facing the same way, then asks the block beyond for an energy receiver.
     */
    @Nullable
    public EnergyPort receiverToPower(Direction side) {
        if (level == null) return null;
        BlockPos pos = worldPosition;
        BlockEntity next = null;
        for (int len = 0; len <= getMaxChainLength(); len++) {
            pos = pos.relative(side);
            next = level.getBlockEntity(pos);
            if (next == null) return null;
            if (next instanceof EngineBlockEntity engine) {
                if (next.getClass() != getClass() || engine.facing() != side) return null;
            } else {
                break;
            }
        }
        if (next == null || next instanceof EngineBlockEntity) return null;
        Direction face = side.getOpposite();
        if (next instanceof PortHolder holder) {
            EnergyPort port = holder.energyPort(face);
            if (port != null && port.canReceive() && (!isRedstoneOnly() || port.acceptsRedstoneEngine())) return port;
            return null;
        }
        if (isRedstoneOnly()) return null;
        EnergyPort port = Platform.get().findEnergy(level, pos, face);
        return port != null && port.canReceive() ? port : null;
    }

    /** Engines push power and expose no storage of their own, as upstream engines expose no receiver. */
    @Nullable
    @Override
    public EnergyPort energyPort(@Nullable Direction side) {
        return null;
    }

    /** True when the engine delivers power into the block on {@code side} (used by kinesis pipes to connect). */
    public boolean outputsTo(Direction side) {
        return facing() == side;
    }

    // Client rendering.

    public float getProgressClient(float partialTicks) {
        float last = lastProgress;
        float now = progress;
        if (last > 0.5 && now < 0.5) now += 1;
        float interp = last * (1 - partialTicks) + now * partialTicks;
        return interp % 1;
    }

    // Persistence.

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("isRedstonePowered", isRedstonePowered);
        tag.putDouble("heat", heat);
        tag.putLong("power", power);
        tag.putFloat("progress", progress);
        tag.putInt("progressPart", progressPart);
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        isRedstonePowered = tag.getBoolean("isRedstonePowered");
        heat = tag.contains("heat") ? tag.getDouble("heat") : MIN_HEAT;
        power = tag.getLong("power");
        progress = tag.getFloat("progress");
        progressPart = tag.getInt("progressPart");
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.putBoolean("pumping", isPumping);
        tag.putFloat("progress", progress);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        isPumping = tag.getBoolean("pumping");
        progress = tag.getFloat("progress");
    }
}
