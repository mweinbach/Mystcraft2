package ports.buildcraft.silicon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.lib.AverageLong;
import ports.buildcraft.lib.BlockUpdateListeners;
import ports.buildcraft.lib.MjBattery;
import ports.buildcraft.lib.Power;
import ports.buildcraft.lib.SafeTimeTracker;
import ports.buildcraft.lib.SyncedBlockEntity;
import ports.buildcraft.lib.VolumeUtil;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.PortHolder;

/**
 * Upstream {@code TileLaser}: stores up to 1024 MJ and beams up to 4 MJ a tick into a random laser table that
 * needs power within a six block square cone in front of it, with a clear line of sight. Upstream sent its render
 * data to clients every tick; this sends it when the target or the beam's colour band changes.
 */
public class LaserBlockEntity extends SyncedBlockEntity implements PortHolder {
    private static final int TARGETING_RANGE = 6;
    public static final long MAX_POWER_PER_TICK = 4 * Power.MJ;
    /** Upstream {@code RenderLaser} draws no beam at or below this average. */
    private static final long RENDER_THRESHOLD = 200_000;

    private final SafeTimeTracker clientLaserMoveInterval = new SafeTimeTracker(5, 10);
    private final SafeTimeTracker serverTargetMoveInterval = new SafeTimeTracker(10, 20);
    private final MjBattery battery = new MjBattery(1024 * Power.MJ);
    private final EnergyPort energyPort = battery.receiver(false, this::setChanged);
    private final List<BlockPos> targetPositions = new ArrayList<>();
    private final AverageLong avgPower = new AverageLong(100);
    private final BlockUpdateListeners.Listener updateListener = this::onBlockUpdate;
    @Nullable private ServerLevel listeningTo;
    @Nullable private BlockPos targetPos;
    @Nullable private Vec3 laserPos;
    private boolean worldHasUpdated = true;
    private long averageClient;
    @Nullable private BlockPos syncedTarget;
    private int syncedBand = -1;

    public LaserBlockEntity(BlockPos pos, BlockState state) {
        super(BCSilicon.LASER_TYPE.get(), pos, state);
    }

    public MjBattery battery() {
        return battery;
    }

    @Nullable
    public BlockPos targetPos() {
        return targetPos;
    }

    @Nullable
    public Vec3 laserPos() {
        return laserPos;
    }

    public long averagePower() {
        return level != null && level.isClientSide ? averageClient : (long) avgPower.getAverage();
    }

    /** The {@code LaserRenderer.POWERS} index upstream's {@code RenderLaser} picks for an average, or -1 for no beam. */
    public static int renderBand(long average) {
        if (average <= RENDER_THRESHOLD) return -1;
        return (int) Math.min(3, (average + RENDER_THRESHOLD) * 3 / MAX_POWER_PER_TICK);
    }

    private void onBlockUpdate(BlockPos pos) {
        if (Math.abs(pos.getX() - worldPosition.getX()) <= TARGETING_RANGE
                && Math.abs(pos.getY() - worldPosition.getY()) <= TARGETING_RANGE
                && Math.abs(pos.getZ() - worldPosition.getZ()) <= TARGETING_RANGE) {
            worldHasUpdated = true;
        }
    }

    private void startListening() {
        if (listeningTo == null && level instanceof ServerLevel serverLevel) {
            listeningTo = serverLevel;
            BlockUpdateListeners.add(serverLevel, updateListener);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (listeningTo != null) {
            BlockUpdateListeners.remove(listeningTo, updateListener);
            listeningTo = null;
        }
    }

    private void findPossibleTargets() {
        targetPositions.clear();
        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof LaserBlock)) return;
        Direction face = state.getValue(LaserBlock.FACING);
        VolumeUtil.iterateCone(level, worldPosition, face, TARGETING_RANGE, (pos, visible) -> {
            if (visible && level.getBlockState(pos).getBlock() instanceof LaserTargetBlock
                    && level.getBlockEntity(pos) instanceof LaserTarget) {
                targetPositions.add(pos);
            }
        });
    }

    private void randomlyChooseTargetPos() {
        List<BlockPos> targetsNeedingPower = new ArrayList<>();
        for (BlockPos position : targetPositions) {
            if (isPowerNeededAt(position)) targetsNeedingPower.add(position);
        }
        targetPos = targetsNeedingPower.isEmpty() ? null : targetsNeedingPower.get(level.random.nextInt(targetsNeedingPower.size()));
    }

    private boolean isPowerNeededAt(@Nullable BlockPos position) {
        return position != null && level.getBlockEntity(position) instanceof LaserTarget target && target.getRequiredLaserPower() > 0;
    }

    @Nullable
    private LaserTarget getTarget() {
        return targetPos != null && level.getBlockEntity(targetPos) instanceof LaserTarget target ? target : null;
    }

    private void updateLaser() {
        laserPos = targetPos == null ? null : Vec3.atLowerCornerOf(targetPos).add(
                (5 + level.random.nextInt(6) + 0.5) / 16D, 9 / 16D, (5 + level.random.nextInt(6) + 0.5) / 16D);
    }

    void clientTick() {
        if (clientLaserMoveInterval.markTimeIfDelay(level) || targetPos == null) updateLaser();
    }

    void serverTick() {
        startListening();
        avgPower.tick();
        if (worldHasUpdated) {
            findPossibleTargets();
            worldHasUpdated = false;
        }
        if (!isPowerNeededAt(targetPos)) targetPos = null;
        if (serverTargetMoveInterval.markTimeIfDelay(level) || !isPowerNeededAt(targetPos)) randomlyChooseTargetPos();

        LaserTarget target = getTarget();
        if (target != null) {
            long max = MAX_POWER_PER_TICK;
            max *= battery.stored() + max;
            max /= battery.capacity() / 2;
            max = Math.min(Math.min(max, MAX_POWER_PER_TICK), target.getRequiredLaserPower());
            long power = battery.extractPower(0, max);
            long excess = target.receiveLaserPower(power);
            if (excess > 0 && !battery.isFull()) battery.addPower(excess);
            avgPower.push(power - excess);
        } else {
            avgPower.clear();
        }

        int band = renderBand((long) avgPower.getAverage());
        if (!Objects.equals(syncedTarget, targetPos) || band != syncedBand) {
            syncedTarget = targetPos;
            syncedBand = band;
            queueSync();
        }
        setChanged();
        flushSync();
    }

    @Nullable
    @Override
    public EnergyPort energyPort(@Nullable Direction side) {
        return energyPort;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("battery", battery.save());
        if (laserPos != null) {
            CompoundTag vec = new CompoundTag();
            vec.putDouble("x", laserPos.x);
            vec.putDouble("y", laserPos.y);
            vec.putDouble("z", laserPos.z);
            tag.put("laser_pos", vec);
        }
        if (targetPos != null) tag.put("target_pos", NbtUtils.writeBlockPos(targetPos));
        avgPower.save(tag, "average_power");
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        battery.load(tag.getCompound("battery"));
        targetPos = tag.contains("target_pos") ? NbtUtils.readBlockPos(tag.getCompound("target_pos")) : null;
        if (tag.contains("laser_pos")) {
            CompoundTag vec = tag.getCompound("laser_pos");
            laserPos = new Vec3(vec.getDouble("x"), vec.getDouble("y"), vec.getDouble("z"));
        } else {
            laserPos = null;
        }
        avgPower.load(tag, "average_power");
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        if (targetPos != null) tag.put("target_pos", NbtUtils.writeBlockPos(targetPos));
        tag.putLong("average", (long) avgPower.getAverage());
    }

    @Override
    protected void readClient(CompoundTag tag) {
        BlockPos target = tag.contains("target_pos") ? NbtUtils.readBlockPos(tag.getCompound("target_pos")) : null;
        if (!Objects.equals(target, targetPos)) {
            targetPos = target;
            updateLaser();
        }
        averageClient = tag.getLong("average");
    }
}
