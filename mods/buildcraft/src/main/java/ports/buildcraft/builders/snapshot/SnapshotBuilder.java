package ports.buildcraft.builders.snapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.lib.BlockUpdateListeners;
import ports.buildcraft.lib.BlockUtil;
import ports.buildcraft.lib.MjBattery;
import ports.buildcraft.lib.Power;

/**
 * Upstream {@code SnapshotBuilder}: scans its box a few blocks per tick against a snapshot, queues up to
 * 16 blocks to break and 16 to place, and spends power on each until it is done. Block changes in the
 * box are rechecked as they happen.
 */
public abstract class SnapshotBuilder<H extends SnapshotHost> {
    private static final int MAX_QUEUE_SIZE = 16;
    protected static final byte CHECK_RESULT_UNKNOWN = 0;
    protected static final byte CHECK_RESULT_CORRECT = 1;
    protected static final byte CHECK_RESULT_TO_BREAK = 2;
    protected static final byte CHECK_RESULT_TO_PLACE = 3;
    private static final byte REQUIRED_UNKNOWN = 0;
    private static final byte REQUIRED_TRUE = 1;
    private static final byte REQUIRED_FALSE = 2;
    private static final int CHECKS_PER_TICK = 10;
    private static final long MAX_POWER_PER_TICK = 256 * Power.MJ;
    private static final Vec3 VEC_HALF = new Vec3(0.5, 0.5, 0.5);

    protected final H host;
    private final BlockUpdateListeners.Listener blockUpdateListener = this::onBlockUpdate;
    @Nullable private ServerLevel listeningTo;
    private final Queue<BreakTask> breakTasks = new ArrayDeque<>();
    public final Queue<BreakTask> clientBreakTasks = new ArrayDeque<>();
    public final Queue<BreakTask> prevClientBreakTasks = new ArrayDeque<>();
    private final Queue<PlaceTask> placeTasks = new ArrayDeque<>();
    public final Queue<PlaceTask> clientPlaceTasks = new ArrayDeque<>();
    public final Queue<PlaceTask> prevClientPlaceTasks = new ArrayDeque<>();
    @Nullable protected byte[] checkResults;
    @Nullable private byte[] requiredCache;
    @Nullable private int[] breakOrder;
    @Nullable private int[] placeOrder;
    @Nullable private int[] checkOrder;
    private int currentCheckIndex;
    @Nullable public Vec3 robotPos;
    @Nullable public Vec3 prevRobotPos;
    public int leftToBreak;
    public int leftToPlace;

    protected SnapshotBuilder(H host) {
        this.host = host;
    }

    /** The snapshot's placement, or null while the host has nothing to build. */
    @Nullable
    protected abstract BuildingInfo buildingInfo();

    protected abstract boolean isAir(BlockPos pos);

    protected abstract boolean canPlace(BlockPos pos);

    protected abstract boolean isReadyToPlace(BlockPos pos);

    protected abstract boolean hasEnoughToPlaceItems(BlockPos pos);

    @Nullable
    protected abstract List<ItemStack> getToPlaceItems(BlockPos pos);

    /** @return true if the block was placed */
    protected abstract boolean doPlaceTask(PlaceTask placeTask);

    /** @return true if the block in the world matches the snapshot, so it need not be broken */
    protected abstract boolean isBlockCorrect(BlockPos pos);

    private Level level() {
        return host.hostLevel();
    }

    private MjBattery battery() {
        return host.battery();
    }

    /** Upstream {@code validate}: starts hearing block changes. */
    public void startListening() {
        if (listeningTo == null && level() instanceof ServerLevel serverLevel) {
            listeningTo = serverLevel;
            BlockUpdateListeners.add(serverLevel, blockUpdateListener);
        }
    }

    /** Upstream {@code invalidate}. */
    public void stopListening() {
        if (listeningTo != null) {
            BlockUpdateListeners.remove(listeningTo, blockUpdateListener);
            listeningTo = null;
        }
    }

    private void onBlockUpdate(BlockPos pos) {
        BuildingInfo info = buildingInfo();
        if (info != null && checkResults != null && info.box.contains(pos) && check(pos)) afterChecks();
    }

    private void cancelBreakTask(BreakTask breakTask) {
        battery().addPower(Math.min(breakTask.power, battery().space()));
    }

    /** Executed if {@link #doPlaceTask} failed. */
    protected void cancelPlaceTask(PlaceTask placeTask) {
        battery().addPower(Math.min(placeTask.power, battery().space()));
    }

    /** Where a flying item should be drawn. */
    public Vec3 getPlaceTaskItemPos(PlaceTask placeTask) {
        BlockPos builderPos = host.builderPos();
        Vec3 height = Vec3.atLowerCornerOf(placeTask.pos.subtract(builderPos));
        double progress = placeTask.power * 1D / Math.max(1, placeTask.getTarget());
        return Vec3.atLowerCornerOf(builderPos)
                .add(height.scale(progress))
                .add(0, Math.sin(progress * Math.PI) * (Math.abs(height.y) + 1), 0)
                .add(0.5, 1, 0.5);
    }

    private int[] order(BuildingInfo info, ToDoubleFunction<BlockPos> key) {
        Comparator<BlockPos> unique = Comparator.comparingDouble(key)
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ);
        return info.box.getBlocksInArea().stream().sorted(unique).mapToInt(this::posToIndex).toArray();
    }

    public void updateSnapshot() {
        BuildingInfo info = buildingInfo();
        if (info == null) return;
        checkResults = new byte[info.volume()];
        requiredCache = new byte[info.volume()];
        BlockPos center = info.box.center();
        BlockPos builderPos = host.builderPos();
        breakOrder = order(info, p -> Math.pow(p.getX() - center.getX(), 2) + Math.pow(p.getZ() - center.getZ(), 2)
                + 100_000 - Math.abs(p.getY() - builderPos.getY()) * 100_000);
        placeOrder = order(info, p -> 100_000 - (Math.pow(p.getX() - builderPos.getX(), 2) + Math.pow(p.getZ() - builderPos.getZ(), 2))
                + Math.abs(p.getY() - builderPos.getY()) * 100_000);
        checkOrder = order(info, p -> Math.pow(p.getX() - center.getX(), 2) + Math.pow(p.getY() - center.getY(), 2)
                + Math.pow(p.getZ() - center.getZ(), 2));
    }

    public void resourcesChanged() {
        if (requiredCache != null) Arrays.fill(requiredCache, REQUIRED_UNKNOWN);
    }

    public void cancel() {
        breakTasks.forEach(this::cancelBreakTask);
        placeTasks.forEach(this::cancelPlaceTask);
        breakTasks.clear();
        clientBreakTasks.clear();
        prevClientBreakTasks.clear();
        placeTasks.clear();
        clientPlaceTasks.clear();
        prevClientPlaceTasks.clear();
        checkResults = null;
        requiredCache = null;
        breakOrder = null;
        placeOrder = null;
        checkOrder = null;
        currentCheckIndex = 0;
        robotPos = null;
        prevRobotPos = null;
        leftToBreak = 0;
        leftToPlace = 0;
    }

    public boolean hasTasks() {
        return !breakTasks.isEmpty() || !placeTasks.isEmpty();
    }

    /** Upstream's client tick: keeps the last two task snapshots and eases the robot towards the blocks being broken. */
    public void clientTick() {
        prevClientBreakTasks.clear();
        prevClientBreakTasks.addAll(clientBreakTasks);
        clientBreakTasks.clear();
        clientBreakTasks.addAll(breakTasks);
        prevClientPlaceTasks.clear();
        prevClientPlaceTasks.addAll(clientPlaceTasks);
        clientPlaceTasks.clear();
        clientPlaceTasks.addAll(placeTasks);
        prevRobotPos = robotPos;
        if (breakTasks.isEmpty()) {
            robotPos = null;
            return;
        }
        Vec3 average = breakTasks.stream()
                .map(task -> Vec3.atLowerCornerOf(task.pos).add(VEC_HALF))
                .reduce(Vec3.ZERO, Vec3::add)
                .scale(1D / breakTasks.size());
        double top = breakTasks.stream().mapToDouble(task -> task.pos.getY()).max().orElse(average.y);
        Vec3 newRobotPos = new Vec3(average.x, top, average.z).add(0, 3, 0);
        Vec3 oldRobotPos = robotPos;
        robotPos = oldRobotPos == null ? newRobotPos : oldRobotPos.add(newRobotPos.subtract(oldRobotPos).scale(1 / 4D));
    }

    /** @return true when building is finished */
    public boolean tick() {
        if (checkResults == null || checkOrder == null || checkOrder.length == 0) return true;
        boolean checkResultsChanged = false;

        for (int i = 0; i < CHECKS_PER_TICK; i++) {
            if (check(indexToPos(currentCheckIndex))) checkResultsChanged = true;
            currentCheckIndex = (currentCheckIndex + 1) % checkOrder.length;
        }

        for (Iterator<BreakTask> iterator = breakTasks.iterator(); iterator.hasNext(); ) {
            BreakTask breakTask = iterator.next();
            if (checkResults[posToIndex(breakTask.pos)] == CHECK_RESULT_CORRECT) {
                iterator.remove();
                cancelBreakTask(breakTask);
            }
        }
        for (Iterator<PlaceTask> iterator = placeTasks.iterator(); iterator.hasNext(); ) {
            PlaceTask placeTask = iterator.next();
            if (checkResults[posToIndex(placeTask.pos)] == CHECK_RESULT_CORRECT) {
                iterator.remove();
                cancelPlaceTask(placeTask);
            }
        }

        boolean isDone = true;

        if (host.canExcavate()) {
            Set<Integer> queued = breakTasks.stream().map(task -> posToIndex(task.pos)).collect(Collectors.toSet());
            int[] blocks = Arrays.stream(breakOrder)
                    .filter(i -> checkResults[i] == CHECK_RESULT_TO_BREAK && !queued.contains(i))
                    .toArray();
            leftToBreak = blocks.length;
            if (blocks.length != 0) isDone = false;
            Arrays.stream(blocks)
                    .mapToObj(this::indexToPos)
                    .filter(pos -> BlockUtil.fluidWithFlowing(level(), pos) == null)
                    .map(pos -> new BreakTask(pos, 0))
                    .limit(MAX_QUEUE_SIZE - breakTasks.size())
                    .forEach(breakTasks::add);
        } else {
            leftToBreak = 0;
        }

        Set<Integer> queuedPlaces = placeTasks.stream().map(task -> posToIndex(task.pos)).collect(Collectors.toSet());
        int[] toPlace = Arrays.stream(placeOrder)
                .filter(i -> checkResults[i] == CHECK_RESULT_TO_PLACE && !queuedPlaces.contains(i))
                .toArray();
        leftToPlace = toPlace.length;
        if (!host.canExcavate() || breakTasks.isEmpty()) {
            if (toPlace.length != 0) isDone = false;
            Arrays.stream(toPlace)
                    .filter(i -> {
                        if (requiredCache[i] != REQUIRED_UNKNOWN) return requiredCache[i] == REQUIRED_TRUE;
                        boolean has = hasEnoughToPlaceItems(indexToPos(i));
                        requiredCache[i] = has ? REQUIRED_TRUE : REQUIRED_FALSE;
                        return has;
                    })
                    .mapToObj(this::indexToPos)
                    .filter(this::isReadyToPlace)
                    .limit(MAX_QUEUE_SIZE - placeTasks.size())
                    .filter(this::canPlace)
                    .map(pos -> {
                        List<ItemStack> items = getToPlaceItems(pos);
                        return items == null ? null : new PlaceTask(pos, items, 0);
                    })
                    .filter(task -> task != null)
                    .forEach(placeTasks::add);
        }

        MjBattery battery = battery();
        long max = Math.min(
                (long) (MAX_POWER_PER_TICK * (double) (battery.stored() + MAX_POWER_PER_TICK / 10) / (battery.capacity() * 2)),
                MAX_POWER_PER_TICK);
        if (!breakTasks.isEmpty()) {
            for (Iterator<BreakTask> iterator = breakTasks.iterator(); iterator.hasNext(); ) {
                BreakTask breakTask = iterator.next();
                if (breakTask.isImpossible()) continue;
                long target = breakTask.getTarget();
                breakTask.power += battery.extractPower(0, Math.min(target - breakTask.power, max / breakTasks.size()));
                if (breakTask.power >= target) {
                    level().destroyBlockProgress(breakTask.pos.hashCode(), breakTask.pos, -1);
                    BlockUtil.breakBlockAndGetDrops((ServerLevel) level(), breakTask.pos);
                    if (check(breakTask.pos)) checkResultsChanged = true;
                    iterator.remove();
                } else {
                    level().destroyBlockProgress(breakTask.pos.hashCode(), breakTask.pos, (int) ((breakTask.power * 9) / target));
                }
            }
        }
        if (!placeTasks.isEmpty()) {
            for (Iterator<PlaceTask> iterator = placeTasks.iterator(); iterator.hasNext(); ) {
                PlaceTask placeTask = iterator.next();
                long target = placeTask.getTarget();
                placeTask.power += battery.extractPower(0, Math.min(target - placeTask.power, max / placeTasks.size()));
                if (placeTask.power >= target) {
                    if (!doPlaceTask(placeTask)) cancelPlaceTask(placeTask);
                    if (check(placeTask.pos)) checkResultsChanged = true;
                    iterator.remove();
                }
            }
        }

        if (checkResultsChanged) afterChecks();
        return isDone;
    }

    protected int posToIndex(BlockPos pos) {
        return buildingInfo().posToIndex(pos);
    }

    protected BlockPos indexToPos(int i) {
        return buildingInfo().indexToPos(i);
    }

    /** @return true if the result changed */
    protected boolean check(BlockPos pos) {
        int i = posToIndex(pos);
        byte prev = checkResults[i];
        if (isAir(pos)) {
            checkResults[i] = level().isEmptyBlock(pos) ? CHECK_RESULT_CORRECT : CHECK_RESULT_TO_BREAK;
        } else if (isBlockCorrect(pos)) {
            checkResults[i] = CHECK_RESULT_CORRECT;
        } else if (canPlace(pos)) {
            checkResults[i] = CHECK_RESULT_TO_PLACE;
        } else {
            checkResults[i] = CHECK_RESULT_TO_BREAK;
        }
        return prev != checkResults[i];
    }

    protected void afterChecks() {}

    // Sync and persistence

    /** Upstream {@code writeToByteBuf}: the queued tasks and counts that the client draws. */
    public void writeClient(CompoundTag tag) {
        tag.put("breakTasks", writeTasks(breakTasks.stream().map(BreakTask::save).toList()));
        tag.put("placeTasks", writeTasks(placeTasks.stream().map(PlaceTask::save).toList()));
        tag.putInt("leftToBreak", leftToBreak);
        tag.putInt("leftToPlace", leftToPlace);
    }

    public void readClient(CompoundTag tag) {
        breakTasks.clear();
        for (Tag task : tag.getList("breakTasks", Tag.TAG_COMPOUND)) breakTasks.add(new BreakTask((CompoundTag) task));
        placeTasks.clear();
        for (Tag task : tag.getList("placeTasks", Tag.TAG_COMPOUND)) placeTasks.add(new PlaceTask((CompoundTag) task));
        leftToBreak = tag.getInt("leftToBreak");
        leftToPlace = tag.getInt("leftToPlace");
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (checkResults != null) tag.putByteArray("checkResults", checkResults);
        tag.put("breakTasks", writeTasks(breakTasks.stream().map(BreakTask::save).toList()));
        tag.put("placeTasks", writeTasks(placeTasks.stream().map(PlaceTask::save).toList()));
        tag.putInt("currentCheckIndex", currentCheckIndex);
        return tag;
    }

    public void load(CompoundTag tag) {
        updateSnapshot();
        byte[] saved = tag.getByteArray("checkResults");
        if (checkResults != null && saved.length == checkResults.length) checkResults = saved;
        breakTasks.clear();
        for (Tag task : tag.getList("breakTasks", Tag.TAG_COMPOUND)) breakTasks.add(new BreakTask((CompoundTag) task));
        placeTasks.clear();
        for (Tag task : tag.getList("placeTasks", Tag.TAG_COMPOUND)) placeTasks.add(new PlaceTask((CompoundTag) task));
        int index = tag.getInt("currentCheckIndex");
        currentCheckIndex = checkOrder == null || checkOrder.length == 0 ? 0 : Math.floorMod(index, checkOrder.length);
    }

    private static ListTag writeTasks(List<CompoundTag> tasks) {
        ListTag list = new ListTag();
        list.addAll(tasks);
        return list;
    }

    public Queue<BreakTask> breakTasks() {
        return breakTasks;
    }

    public Queue<PlaceTask> placeTasks() {
        return placeTasks;
    }

    public final class BreakTask {
        public final BlockPos pos;
        public long power;

        BreakTask(BlockPos pos, long power) {
            this.pos = pos;
            this.power = power;
        }

        BreakTask(CompoundTag tag) {
            pos = NbtUtils.readBlockPos(tag.getCompound("pos"));
            power = tag.getLong("power");
        }

        public boolean isImpossible() {
            return BlockUtil.isUnbreakableBlock(level(), pos);
        }

        public long getTarget() {
            return BlockUtil.computeBlockBreakPower(level(), pos);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("pos", NbtUtils.writeBlockPos(pos));
            tag.putLong("power", power);
            return tag;
        }
    }

    public final class PlaceTask {
        public final BlockPos pos;
        public final List<ItemStack> items;
        public long power;

        PlaceTask(BlockPos pos, List<ItemStack> items, long power) {
            this.pos = pos;
            this.items = List.copyOf(items);
            this.power = power;
        }

        PlaceTask(CompoundTag tag) {
            pos = NbtUtils.readBlockPos(tag.getCompound("pos"));
            List<ItemStack> read = new ArrayList<>();
            for (Tag item : tag.getList("items", Tag.TAG_COMPOUND)) read.add(ItemStack.of((CompoundTag) item));
            items = List.copyOf(read);
            power = tag.getLong("power");
        }

        public long getTarget() {
            return (long) (Math.sqrt(pos.distSqr(host.builderPos())) * 10 * Power.MJ);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("pos", NbtUtils.writeBlockPos(pos));
            ListTag list = new ListTag();
            for (ItemStack item : items) list.add(item.save(new CompoundTag()));
            tag.put("items", list);
            tag.putLong("power", power);
            return tag;
        }
    }
}
