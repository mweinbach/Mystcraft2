package ports.buildcraft.builders;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.lib.BlockUtil;
import ports.buildcraft.lib.Box;
import ports.buildcraft.lib.BoxIterator;
import ports.buildcraft.lib.InventoryUtil;
import ports.buildcraft.lib.MjBattery;
import ports.buildcraft.lib.Power;
import ports.buildcraft.lib.SyncedBlockEntity;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.platform.PortHolder;

/**
 * Upstream {@code TileQuarry}: builds a frame around its area, then moves a drill over the area layer
 * by layer, spending power on each move and block break.
 */
public class QuarryBlockEntity extends SyncedBlockEntity implements PortHolder {
    public static final long MAX_POWER_PER_TICK = 512 * Power.MJ;
    public static final long FRAME_POWER = 24 * Power.MJ;
    /** Upstream {@code BCBuildersConfig} defaults. */
    private static final int FRAME_MIN_HEIGHT = 4;
    private static final int MAX_TASKS_PER_TICK = 4;
    private static final int TASK_POWER_DIVISOR = 2;
    private static final int MAX_DEPTH = 512;

    private final MjBattery battery = new MjBattery(24_000 * Power.MJ);
    private final EnergyPort energyPort = battery.receiver(false, this::setChanged);
    public final Box frameBox = new Box();
    final Box miningBox = new Box();
    @Nullable private BoxIterator boxIterator;
    public final List<BlockPos> framePoses = new ArrayList<>();
    private int frameBoxPosesCount;
    private final LinkedList<BlockPos> toCheck = new LinkedList<>();
    private final Set<BlockPos> firstCheckedPoses = new HashSet<>();
    private boolean firstChecked;
    private final Set<BlockPos> frameBreakBlockPoses = new TreeSet<>(uniqueComparator());
    private final Set<BlockPos> framePlaceFramePoses = new HashSet<>();
    @Nullable Task currentTask;
    @Nullable public Vec3 drillPos;
    private boolean posesDirty = true;

    @Nullable private Vec3 clientDrillPos;
    @Nullable private Vec3 prevClientDrillPos;

    public QuarryBlockEntity(BlockPos pos, BlockState state) {
        super(BCBuilders.QUARRY_TYPE.get(), pos, state);
    }

    private Comparator<BlockPos> uniqueComparator() {
        Comparator<BlockPos> byDistance = Comparator.comparingDouble(p -> worldPosition.distSqr(p));
        return byDistance.thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ);
    }

    public MjBattery battery() {
        return battery;
    }

    public boolean isFrameComplete() {
        return firstChecked && frameBreakBlockPoses.isEmpty() && framePlaceFramePoses.isEmpty();
    }

    @Nullable
    public BlockPos currentMiningTarget() {
        return boxIterator == null ? null : boxIterator.getCurrent();
    }

    private BoxIterator createBoxIterator() {
        long x = worldPosition.getX();
        long y = worldPosition.getY();
        long z = worldPosition.getZ();
        // Upstream's seed, including its operator-precedence quirk on z, keeps the same dig order.
        long seed = (x & 0xFFFF) | ((y & 0xFFFF) << 16) | (z & 0xFFFF << 32);
        Random rand = new Random(seed);
        boolean xFirst = rand.nextBoolean();
        Direction.Axis firstAxis = xFirst ? Direction.Axis.X : Direction.Axis.Z;
        Direction.Axis secondAxis = xFirst ? Direction.Axis.Z : Direction.Axis.X;
        Direction first = Direction.fromAxisAndDirection(firstAxis, rand.nextBoolean() ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        Direction second = Direction.fromAxisAndDirection(secondAxis, rand.nextBoolean() ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        return new BoxIterator(miningBox.min(), miningBox.max(), first, second, Direction.DOWN, true);
    }

    /** Upstream {@code getFramePositions}: flood the frame's edges outward from the quarry, in placement order. */
    private List<BlockPos> getFramePositions() {
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> framePositions = new ArrayList<>();
        List<BlockPos> openSet = new ArrayList<>();
        List<BlockPos> nextOpenSet = new ArrayList<>();
        openSet.add(worldPosition);
        List<Direction> order = Arrays.asList(Direction.values());
        int maxIterationCount = frameBox.getBlocksOnEdgeCount();
        int iterationCount = 0;
        Random random = new Random(level.random.nextLong());
        do {
            for (BlockPos p : openSet) {
                Collections.shuffle(order, random);
                for (Direction face : order) {
                    BlockPos next = p.relative(face);
                    if (frameBox.isOnEdge(next) && visited.add(next)) {
                        nextOpenSet.add(next);
                        framePositions.add(next);
                    }
                }
            }
            openSet.clear();
            List<BlockPos> swap = openSet;
            openSet = nextOpenSet;
            nextOpenSet = swap;
            Collections.shuffle(openSet, random);
            iterationCount++;
        } while (!openSet.isEmpty() && iterationCount < maxIterationCount);
        return framePositions;
    }

    private boolean shouldBeFrame(BlockPos p) {
        return frameBox.isOnEdge(p);
    }

    /** Upstream {@code onPlacedBy}: the default 11x11 area behind the quarry (landmarks are not ported). */
    void onPlaced() {
        Direction facing = getBlockState().getValue(QuarryBlock.FACING);
        BlockPos min;
        BlockPos max;
        switch (facing.getOpposite()) {
            case WEST -> { min = worldPosition.offset(-11, 0, -5); max = worldPosition.offset(-1, 4, 5); }
            case SOUTH -> { min = worldPosition.offset(-5, 0, 1); max = worldPosition.offset(5, 4, 11); }
            case NORTH -> { min = worldPosition.offset(-5, 0, -11); max = worldPosition.offset(5, 4, -1); }
            default -> { min = worldPosition.offset(1, 0, -5); max = worldPosition.offset(11, 4, 5); }
        }
        setArea(min, max);
    }

    /** Sets the frame box; the mining box is its interior extended down to the mining depth. */
    public void setArea(BlockPos min, BlockPos max) {
        if (max.getY() - min.getY() < FRAME_MIN_HEIGHT) max = new BlockPos(max.getX(), min.getY() + FRAME_MIN_HEIGHT, max.getZ());
        if (level.isOutsideBuildHeight(max)) {
            int dist = max.getY() - min.getY();
            min = min.below(dist);
            max = max.below(dist);
        }
        frameBox.set(min, max);
        int minY = Math.max(level.getMinBuildHeight(), max.getY() - 1 - MAX_DEPTH);
        miningBox.set(new BlockPos(min.getX() + 1, minY, min.getZ() + 1), new BlockPos(max.getX() - 1, max.getY() - 1, max.getZ() - 1));
        boxIterator = null;
        drillPos = null;
        currentTask = null;
        updatePoses();
        setChanged();
        queueSync();
    }

    private boolean canMine(BlockPos pos) {
        if (level.getBlockState(pos).getDestroySpeed(level, pos) < 0) return false;
        Fluid fluid = BlockUtil.fluidWithFlowing(level, pos);
        return fluid == null || Platform.get().viscosity(fluid) <= 1000;
    }

    private boolean canMoveThrough(BlockPos pos) {
        if (level.isEmptyBlock(pos)) return true;
        Fluid fluid = BlockUtil.fluidWithFlowing(level, pos);
        return fluid != null && Platform.get().viscosity(fluid) <= 1000;
    }

    private boolean canMoveDownTo(BlockPos pos) {
        for (int y = miningBox.max().getY(); y > pos.getY(); y--) {
            if (!canMoveThrough(new BlockPos(pos.getX(), y, pos.getZ()))) return false;
        }
        return true;
    }

    private boolean canIgnoreInFrameBox(BlockPos pos) {
        return !level.isEmptyBlock(pos) && BlockUtil.fluidWithFlowing(level, pos) == null;
    }

    private void check(BlockPos pos) {
        frameBreakBlockPoses.remove(pos);
        framePlaceFramePoses.remove(pos);
        if (shouldBeFrame(pos)) {
            if (!level.getBlockState(pos).is(BCBuilders.FRAME.get())) {
                if (canIgnoreInFrameBox(pos)) frameBreakBlockPoses.add(pos);
                else framePlaceFramePoses.add(pos);
            }
        } else if (canIgnoreInFrameBox(pos)) {
            frameBreakBlockPoses.add(pos);
        }
        if (!firstChecked) {
            firstCheckedPoses.add(pos);
            if (firstCheckedPoses.size() >= frameBoxPosesCount) firstChecked = true;
        }
    }

    private void updatePoses() {
        posesDirty = false;
        framePoses.clear();
        frameBoxPosesCount = 0;
        toCheck.clear();
        firstCheckedPoses.clear();
        firstChecked = false;
        frameBreakBlockPoses.clear();
        framePlaceFramePoses.clear();
        if (getBlockState().getBlock() instanceof QuarryBlock && frameBox.isInitialized()) {
            List<BlockPos> blocksInArea = frameBox.getBlocksInArea();
            blocksInArea.sort(uniqueComparator());
            frameBoxPosesCount = blocksInArea.size();
            toCheck.addAll(blocksInArea);
            framePoses.addAll(getFramePositions());
        }
    }

    void clientTick() {
        prevClientDrillPos = clientDrillPos;
        clientDrillPos = drillPos;
        if (currentTask != null) currentTask.clientTick();
    }

    void serverTick() {
        if (posesDirty) updatePoses();
        battery.tick();
        if (!frameBox.isInitialized() || !miningBox.isInitialized()) {
            flushSync();
            return;
        }
        if (!toCheck.isEmpty()) {
            for (int i = 0; i < (firstChecked ? 10 : 500); i++) {
                BlockPos pos = toCheck.pollFirst();
                check(pos);
                toCheck.addLast(pos);
            }
        }
        if (!firstChecked) return;

        long max;
        if (battery.stored() > battery.capacity() / 2) {
            max = MAX_POWER_PER_TICK;
        } else {
            long roundedUp = battery.stored() + Power.MJ / 2;
            if (roundedUp > Long.MAX_VALUE / MAX_POWER_PER_TICK) {
                max = BigInteger.valueOf(roundedUp).multiply(BigInteger.valueOf(MAX_POWER_PER_TICK))
                        .divide(BigInteger.valueOf(battery.capacity() / 2)).longValue();
            } else {
                max = MAX_POWER_PER_TICK * roundedUp / (battery.capacity() / 2);
            }
            max = Math.max(0, Math.min(MAX_POWER_PER_TICK, max));
        }

        int maxTasks = Math.max(1, (int) (max * MAX_TASKS_PER_TICK / MAX_POWER_PER_TICK));
        boolean sendUpdate = false;
        powerLoop:
        for (int i = 0; i < maxTasks; i++) {
            if (currentTask != null) {
                long needed = currentTask.getRequiredPowerThisTick();
                int mult = TASK_POWER_DIVISOR;
                long nNeeded = needed * (mult + i) / mult;
                long leftover = (needed * (mult + i)) % mult;
                long power = battery.extractPower(0, Math.min(max, nNeeded));
                max -= power;
                long added = power * mult / (mult + i);
                if (leftover > 0) added++;
                if (currentTask.addPower(added)) {
                    currentTask = null;
                } else {
                    sendUpdate = true;
                    break;
                }
            }

            if (!frameBreakBlockPoses.isEmpty()) {
                BlockPos pos = frameBreakBlockPoses.iterator().next();
                if (canMine(pos)) {
                    drillPos = null;
                    currentTask = new TaskBreakBlock(pos);
                    sendUpdate = true;
                }
                check(pos);
                continue;
            }

            if (!framePlaceFramePoses.isEmpty()) {
                for (BlockPos pos : framePoses) {
                    if (!framePlaceFramePoses.contains(pos)) continue;
                    check(pos);
                    if (!framePlaceFramePoses.contains(pos)) continue;
                    drillPos = null;
                    currentTask = new TaskAddFrame(pos);
                    sendUpdate = true;
                    continue powerLoop;
                }
            }

            if (boxIterator == null || drillPos == null) {
                boxIterator = createBoxIterator();
                skipUnmineable();
                drillPos = Vec3.atLowerCornerOf(miningBox.closestInsideTo(worldPosition));
            }

            if (boxIterator.hasNext()) {
                skipUnmineable();
                if (boxIterator.hasNext()) {
                    BlockPos current = boxIterator.getCurrent();
                    if (drillPos.distanceToSqr(Vec3.atLowerCornerOf(current)) >= 1) {
                        currentTask = new TaskMoveDrill(drillPos, Vec3.atLowerCornerOf(current));
                        sendUpdate = true;
                    } else if (canMine(current)) {
                        currentTask = new TaskBreakBlock(current);
                        sendUpdate = true;
                    }
                }
            }
        }
        if (sendUpdate) queueSync();
        setChanged();
        flushSync();
    }

    private void skipUnmineable() {
        while (boxIterator.getCurrent() != null
                && (canMoveThrough(boxIterator.getCurrent()) || !canMine(boxIterator.getCurrent()) || !canMoveDownTo(boxIterator.getCurrent()))) {
            if (boxIterator.advance() == null) break;
        }
    }

    /** Removes the frame this quarry built, as upstream {@code BlockQuarry.breakBlock}. */
    void onRemoved() {
        for (BlockPos pos : framePoses) {
            if (level.getBlockState(pos).is(BCBuilders.FRAME.get())) level.removeBlock(pos, false);
        }
    }

    @Nullable
    @Override
    public EnergyPort energyPort(@Nullable Direction side) {
        return energyPort;
    }

    @Nullable
    @Override
    public ItemPort itemPort(@Nullable Direction side) {
        return InventoryUtil.PROVIDING;
    }

    // Client rendering

    @Nullable
    public Vec3 drillPosForRender(float partialTicks) {
        if (clientDrillPos == null || prevClientDrillPos == null) return null;
        return prevClientDrillPos.add(clientDrillPos.subtract(prevClientDrillPos).scale(partialTicks));
    }

    /**
     * Upstream {@code RenderQuarry}: how far above the drill target the bit sits, bobbing down as a
     * break task gains power.
     */
    public double drillOffsetForRender(float partialTicks) {
        if (currentTask instanceof TaskBreakBlock task && drillPos != null) {
            long power = (long) (task.prevClientPower + (task.clientPower - task.prevClientPower) * (double) partialTicks);
            long target = Math.max(1, task.getTarget());
            double value = (double) power / target;
            value = value < 0.9 ? 1 - value / 0.9 : (value - 0.9) / 0.1;
            double scaleMin = 0.5;
            double scaleMax = 1 + 4 / 16D;
            return scaleMin + value * (scaleMax - scaleMin);
        }
        return 1 + 4 / 16D;
    }

    // Persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("box", miningBox.save());
        tag.put("frame", frameBox.save());
        if (boxIterator != null) tag.put("boxIterator", boxIterator.save());
        tag.put("battery", battery.save());
        if (currentTask != null) tag.put("task", currentTask.save());
        if (drillPos != null) writeVec(tag, "drillPos", drillPos);
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        miningBox.load(tag.getCompound("box"));
        frameBox.load(tag.getCompound("frame"));
        boxIterator = tag.contains("boxIterator") ? BoxIterator.load(tag.getCompound("boxIterator")) : null;
        battery.load(tag.getCompound("battery"));
        currentTask = tag.contains("task") ? Task.load(this, tag.getCompound("task")) : null;
        drillPos = tag.contains("drillPos") ? readVec(tag, "drillPos") : null;
        posesDirty = true;
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.put("frame", frameBox.save());
        if (drillPos != null) writeVec(tag, "drillPos", drillPos);
        if (currentTask != null) tag.put("task", currentTask.save());
    }

    @Override
    protected void readClient(CompoundTag tag) {
        frameBox.load(tag.getCompound("frame"));
        drillPos = tag.contains("drillPos") ? readVec(tag, "drillPos") : null;
        if (tag.contains("task")) {
            Task task = Task.load(this, tag.getCompound("task"));
            if (task != null && currentTask != null && task.sameAs(currentTask)) currentTask.power = task.power;
            else currentTask = task;
        } else {
            currentTask = null;
        }
    }

    private static void writeVec(CompoundTag tag, String key, Vec3 vec) {
        CompoundTag child = new CompoundTag();
        child.putDouble("x", vec.x);
        child.putDouble("y", vec.y);
        child.putDouble("z", vec.z);
        tag.put(key, child);
    }

    private static Vec3 readVec(CompoundTag tag, String key) {
        CompoundTag child = tag.getCompound(key);
        return new Vec3(child.getDouble("x"), child.getDouble("y"), child.getDouble("z"));
    }

    // Tasks

    abstract static class Task {
        final QuarryBlockEntity quarry;
        long power;
        long clientPower;
        long prevClientPower;

        Task(QuarryBlockEntity quarry) {
            this.quarry = quarry;
        }

        abstract String type();
        abstract long getTarget();
        abstract boolean onReceivePower(long added, long target);
        abstract boolean finish(long added, long target);
        abstract boolean sameAs(Task other);

        long getRequiredPowerThisTick() {
            return Math.max(0, getTarget() - power);
        }

        void clientTick() {
            prevClientPower = clientPower;
            clientPower = power;
        }

        /** @return true when the task completed or was cancelled */
        final boolean addPower(long microJoules) {
            power += microJoules;
            long target = getTarget();
            if (power >= target) {
                if (!finish(microJoules, target)) {
                    quarry.battery.addPower(Math.min(power, quarry.battery.capacity() - quarry.battery.stored()));
                }
                return true;
            }
            return onReceivePower(microJoules, target);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", type());
            tag.putLong("power", power);
            return tag;
        }

        @Nullable
        static Task load(QuarryBlockEntity quarry, CompoundTag tag) {
            Task task = switch (tag.getString("type")) {
                case "break" -> quarry.new TaskBreakBlock(NbtUtils.readBlockPos(tag.getCompound("pos")));
                case "frame" -> quarry.new TaskAddFrame(NbtUtils.readBlockPos(tag.getCompound("pos")));
                case "move" -> quarry.new TaskMoveDrill(readVec(tag, "from"), readVec(tag, "to"));
                default -> null;
            };
            if (task != null) task.power = Math.max(0, tag.getLong("power"));
            return task;
        }
    }

    public final class TaskBreakBlock extends Task {
        public final BlockPos breakPos;

        TaskBreakBlock(BlockPos breakPos) {
            super(QuarryBlockEntity.this);
            this.breakPos = breakPos;
        }

        @Override String type() { return "break"; }

        @Override
        long getTarget() {
            return BlockUtil.computeBlockBreakPower(level, breakPos);
        }

        @Override
        boolean onReceivePower(long added, long target) {
            if (!level.isEmptyBlock(breakPos)) {
                level.destroyBlockProgress(breakPos.hashCode(), breakPos, (int) (power * 9 / getTarget()));
                return false;
            }
            return true;
        }

        @Override
        boolean finish(long added, long target) {
            if (!canMine(breakPos)) return true;
            level.destroyBlockProgress(breakPos.hashCode(), breakPos, -1);
            List<ItemStack> stacks = BlockUtil.breakBlockAndGetDrops((ServerLevel) level, breakPos);
            // While building the frame there is no drill: obstacles are destroyed, not collected.
            if (drillPos != null) stacks.forEach(stack -> InventoryUtil.addToBestAcceptor(level, worldPosition, null, stack));
            check(breakPos);
            return true;
        }

        @Override
        boolean sameAs(Task other) {
            return other instanceof TaskBreakBlock task && task.breakPos.equals(breakPos);
        }

        @Override
        CompoundTag save() {
            CompoundTag tag = super.save();
            tag.put("pos", NbtUtils.writeBlockPos(breakPos));
            return tag;
        }
    }

    public final class TaskAddFrame extends Task {
        public final BlockPos framePos;

        TaskAddFrame(BlockPos framePos) {
            super(QuarryBlockEntity.this);
            this.framePos = framePos;
        }

        @Override String type() { return "frame"; }
        @Override long getTarget() { return FRAME_POWER; }

        @Override
        boolean onReceivePower(long added, long target) {
            return canIgnoreInFrameBox(framePos);
        }

        @Override
        boolean finish(long added, long target) {
            if (canIgnoreInFrameBox(framePos)) return false;
            level.setBlock(framePos, Block.updateFromNeighbourShapes(BCBuilders.FRAME.get().defaultBlockState(), level, framePos), 3);
            return true;
        }

        @Override
        boolean sameAs(Task other) {
            return other instanceof TaskAddFrame task && task.framePos.equals(framePos);
        }

        @Override
        CompoundTag save() {
            CompoundTag tag = super.save();
            tag.put("pos", NbtUtils.writeBlockPos(framePos));
            return tag;
        }
    }

    public final class TaskMoveDrill extends Task {
        final Vec3 from;
        final Vec3 to;

        TaskMoveDrill(Vec3 from, Vec3 to) {
            super(QuarryBlockEntity.this);
            this.from = from;
            this.to = to;
        }

        @Override String type() { return "move"; }

        @Override
        long getTarget() {
            return (long) (from.distanceTo(to) * 20 * Power.MJ);
        }

        @Override
        boolean onReceivePower(long added, long target) {
            drillPos = from.scale(1 - power / (double) target).add(to.scale(power / (double) target));
            return false;
        }

        @Override
        boolean finish(long added, long target) {
            drillPos = to;
            return true;
        }

        @Override
        boolean sameAs(Task other) {
            return other instanceof TaskMoveDrill task && task.from.equals(from) && task.to.equals(to);
        }

        @Override
        CompoundTag save() {
            CompoundTag tag = super.save();
            writeVec(tag, "from", from);
            writeVec(tag, "to", to);
            return tag;
        }
    }
}
