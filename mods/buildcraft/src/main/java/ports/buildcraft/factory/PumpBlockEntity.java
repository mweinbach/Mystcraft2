package ports.buildcraft.factory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.lib.BlockUtil;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.Ports;
import ports.buildcraft.lib.Power;
import ports.buildcraft.lib.Tank;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.Platform;

/**
 * Upstream {@code TilePump}: lowers a tube to the first fluid below, floods outward through connected
 * fluid of the same kind and drains the farthest source blocks first, at 10 MJ per bucket.
 */
public class PumpBlockEntity extends MinerBlockEntity {
    /** Upstream {@code BCCoreConfig.pumpMaxDistance} default. */
    public static final int MAX_DISTANCE = 64;
    private static final long DRAIN_POWER = 10 * Power.MJ;
    private static final int REBUILD_DELAY = 30;
    private static final Direction[] SEARCH_NORMAL = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final Direction[] SEARCH_GASEOUS = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private record FluidPath(BlockPos thisPos, @Nullable FluidPath parent) {}

    final Tank tank = new Tank("tank", 16_000);
    private final FluidPort port = new PumpPort();
    private boolean queueBuilt;
    private final Map<BlockPos, FluidPath> paths = new HashMap<>();
    private final Deque<BlockPos> queue = new ArrayDeque<>();
    private boolean isInfiniteWaterSource;
    private long lastRebuild = -REBUILD_DELAY;
    @Nullable private BlockPos targetPos;

    public PumpBlockEntity(BlockPos pos, BlockState state) {
        super(BCFactory.PUMP_TYPE.get(), pos, state);
    }

    public Tank tank() {
        return tank;
    }

    public boolean isInfiniteWaterSource() {
        return isInfiniteWaterSource;
    }

    @Override
    protected long getBatteryCapacity() {
        return 50 * Power.MJ;
    }

    @Override
    protected boolean acceptsRedstoneEngine() {
        return true;
    }

    private void buildQueue() {
        queue.clear();
        paths.clear();
        Fluid queueFluid = null;
        isInfiniteWaterSource = false;
        Set<BlockPos> checked = new HashSet<>();
        List<BlockPos> nextPosesToCheck = new ArrayList<>();
        for (targetPos = worldPosition.below(); !level.isOutsideBuildHeight(targetPos); targetPos = targetPos.below()) {
            if (worldPosition.getY() - targetPos.getY() > MAX_DEPTH) break;
            Fluid fluid = BlockUtil.fluidWithFlowing(level, targetPos);
            if (fluid != null) {
                queueFluid = fluid;
                nextPosesToCheck.add(targetPos);
                paths.put(targetPos, new FluidPath(targetPos, null));
                checked.add(targetPos);
                if (BlockUtil.drainableFluid(level, targetPos) != null) queue.add(targetPos);
                break;
            }
            if (!level.isEmptyBlock(targetPos) && !level.getBlockState(targetPos).is(BCFactory.TUBE.get())) break;
        }
        if (nextPosesToCheck.isEmpty() || queueFluid == null) return;
        buildQueue0(queueFluid, nextPosesToCheck, checked);
    }

    private void buildQueue0(Fluid queueFluid, List<BlockPos> nextPosesToCheck, Set<BlockPos> checked) {
        Direction[] directions = Platform.get().isGaseous(queueFluid) ? SEARCH_GASEOUS : SEARCH_NORMAL;
        boolean isWater = queueFluid == Fluids.WATER;
        int maxLengthSquared = MAX_DISTANCE * MAX_DISTANCE;
        outer:
        while (!nextPosesToCheck.isEmpty()) {
            List<BlockPos> toCheck = new ArrayList<>(nextPosesToCheck);
            nextPosesToCheck.clear();
            for (BlockPos posToCheck : toCheck) {
                int count = 0;
                for (Direction side : directions) {
                    BlockPos offsetPos = posToCheck.relative(side);
                    if (offsetPos.distSqr(targetPos) > maxLengthSquared) continue;
                    if (checked.add(offsetPos)) {
                        if (BlockUtil.fluidWithFlowing(level, offsetPos) == queueFluid) {
                            paths.put(offsetPos, new FluidPath(offsetPos, paths.get(posToCheck)));
                            if (BlockUtil.drainableFluid(level, offsetPos) != null) queue.add(offsetPos);
                            nextPosesToCheck.add(offsetPos);
                            count++;
                        }
                    } else {
                        // Already visited, so it must be a valid source of the same fluid.
                        count++;
                    }
                }
                if (isWater && count >= 2) {
                    // Same check as vanilla water source creation.
                    BlockState below = level.getBlockState(posToCheck.below());
                    if (BlockUtil.drainableFluid(level, posToCheck.below()) == Fluids.WATER || below.getMaterial().isSolid()) {
                        isInfiniteWaterSource = true;
                        break outer;
                    }
                }
            }
        }
    }

    private boolean canDrain(BlockPos pos) {
        Fluid fluid = BlockUtil.drainableFluid(level, pos);
        return tank.isEmpty() ? fluid != null : fluid == tank.fluid();
    }

    private void nextPos() {
        while (!queue.isEmpty()) {
            currentPos = queue.removeLast();
            if (canDrain(currentPos)) {
                updateLength();
                return;
            }
        }
        currentPos = null;
        updateLength();
    }

    @Nullable
    @Override
    protected BlockPos getTargetPos() {
        return queue.isEmpty() ? null : targetPos;
    }

    @Override
    void serverTick() {
        if (!queueBuilt) {
            buildQueue();
            queueBuilt = true;
        }
        super.serverTick();
        Ports.pushFluidAround(level, worldPosition, tank);
    }

    private boolean markTimeIfDelay() {
        long now = level.getGameTime();
        if (now < lastRebuild || now - lastRebuild >= REBUILD_DELAY) {
            lastRebuild = now;
            return true;
        }
        return false;
    }

    @Override
    protected void mine() {
        if (tank.amount() > tank.capacity() / 2) return;
        if (currentPos != null && paths.containsKey(currentPos)) {
            progress += battery.extractPower(0, DRAIN_POWER - progress);
            if (progress < DRAIN_POWER) return;
            Fluid drain = BlockUtil.drainBlock(level, currentPos, false);
            if (drain != null && getFirstInvalidPointOnPath(currentPos) == null && canDrain(currentPos)) {
                tank.fill(drain, 1000, false);
                progress = 0;
                if (isInfiniteWaterSource) isInfiniteWaterSource = drain == Fluids.WATER;
                if (!isInfiniteWaterSource) {
                    BlockUtil.drainBlock(level, currentPos, true);
                    paths.remove(currentPos);
                    nextPos();
                }
                setChanged();
                return;
            }
            if (!markTimeIfDelay()) return;
        } else if (currentPos == null && !markTimeIfDelay()) {
            return;
        }
        buildQueue();
        nextPos();
    }

    @Nullable
    private BlockPos getFirstInvalidPointOnPath(BlockPos from) {
        FluidPath path = paths.get(from);
        if (path == null) return from;
        do {
            if (BlockUtil.fluidWithFlowing(level, path.thisPos()) == null) return path.thisPos();
        } while ((path = path.parent()) != null);
        return null;
    }

    @Nullable
    @Override
    public FluidPort fluidPort(@Nullable Direction side) {
        return port;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("tank", tank.save());
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        super.loadServer(tag);
        tank.load(tag.getCompound("tank"));
    }

    /** Upstream {@code tank.setCanFill(false)}: the pump's tank can only be drained from outside. */
    private final class PumpPort implements FluidPort {
        @Override public long fill(Fluid fluid, long amount, boolean simulate) { return 0; }

        @Override
        public FluidAmount drain(@Nullable Fluid filter, long amount, boolean simulate) {
            FluidAmount drained = tank.drain(filter, (int) Math.min(Integer.MAX_VALUE, amount), simulate);
            if (!simulate && !drained.isEmpty()) setChanged();
            return drained;
        }

        @Override public List<FluidAmount> contents() { return List.of(tank.contents()); }
        @Override public long capacity(int index) { return tank.capacity(); }
        @Override public Object snapshot() { return tank.snapshotValue(); }
        @Override public void restore(Object snapshot) { tank.restoreValue((Long) snapshot); }
        @Override public void onCommitted() { setChanged(); }
    }
}
