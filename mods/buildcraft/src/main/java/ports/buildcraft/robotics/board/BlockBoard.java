package ports.buildcraft.robotics.board;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.lib.Power;
import ports.buildcraft.robotics.DockingStationPluggable;
import ports.buildcraft.robotics.Progress;
import ports.buildcraft.robotics.RobotEntity;
import ports.buildcraft.robotics.StationRef;
import ports.buildcraft.robotics.WorkArea;

/**
 * Boards that work block by block in the robot's work area, written for this port. The robot gets ready (a board
 * may first fetch a tool or items from beside its home station), looks for the nearest block that is work for its
 * board over several ticks, claims it so other robots leave it alone, flies beside it and works on it. Blocks it
 * fails on (protection refused, or the work cannot be done) are skipped until it next rests. It brings what it
 * collects home once it is full or runs out of work, and rests at home when there is nothing to do.
 */
public abstract class BlockBoard extends RobotBoard {
    public static final int IDLE_TICKS = 100;
    /** Positions checked per tick while looking for work: a 64-block cube takes 32 ticks, the default area 5. */
    public static final int SCAN_BUDGET = 8192;
    /** What a quick job on one block, such as tilling, planting or using an item, costs. */
    public static final long USE_COST = 10 * Power.MJ;
    private static final Direction[] APPROACH = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN};

    @Nullable
    private BlockPos target;
    private boolean unloading, restAfterUnloading;
    /** Blocks the robot failed to work on, skipped until it next rests. */
    protected final Set<BlockPos> skipped = new HashSet<>();
    /** For boards that work with items: items that found no work in a whole sweep, left at home while there are others. */
    private final Set<Item> idleItems = new HashSet<>();
    private boolean usesItems;
    /** The sweep in progress over {@link #scanArea}, and the nearest work it has found so far. */
    @Nullable
    private Iterator<BlockPos> scan;
    @Nullable
    private WorkArea scanArea;
    @Nullable
    private BlockPos scanBest;
    private double scanBestDistance;

    protected BlockBoard(RobotEntity robot) {
        super(robot);
    }

    /** Whether the block at {@code pos} is work for this board; the area, claims and skipped blocks are checked already. */
    protected abstract boolean isWork(Level level, BlockPos pos);

    /** Works on {@code pos} while the robot hovers beside it; {@link Progress#FAILED} skips the block. */
    protected abstract Progress work(BlockPos pos);

    /** Gets the robot ready to work, for example by fetching a tool; false while it is still busy with that. */
    protected boolean prepare() {
        return true;
    }

    /** Whether the robot carries something to bring home. */
    protected boolean hasCargo() {
        return !robot.isInventoryEmpty();
    }

    /** Whether the robot must go home and unload before it works on. */
    protected boolean isFull() {
        return robot.isInventoryFull();
    }

    /** Brings the cargo home; see {@link #unloadAtHome()}. */
    protected Progress unload() {
        return unloadAtHome();
    }

    /** Sends the robot home to unload; with {@code rest} it rests there afterwards. */
    protected void startUnloading(boolean rest) {
        unloading = true;
        restAfterUnloading |= rest;
    }

    @Override
    protected void cargoToUnload() {
        startUnloading(false);
    }

    @Override
    public void tick() {
        if (unloading) {
            Progress progress = unload();
            if (progress == Progress.WORKING) return;
            unloading = false;
            if (progress == Progress.FAILED || restAfterUnloading) rest();
            restAfterUnloading = false;
            return;
        }
        if (isFull()) {
            startUnloading(false);
            return;
        }
        if (!prepare()) return;
        WorkArea area = robot.workArea();
        if (target == null || !isTarget(target, area)) {
            release();
            if (!scanStep(area)) return;
            BlockPos found = scanBest;
            scanBest = null;
            if (found != null && !isTarget(found, area)) return; // changed during the sweep: look again
            if (found == null) {
                if (usesItems) {
                    for (int slot = 0; slot < robot.inventory().getContainerSize(); slot++) {
                        ItemStack stack = robot.inventory().getItem(slot);
                        if (!stack.isEmpty()) idleItems.add(stack.getItem());
                    }
                }
                if (hasCargo()) startUnloading(true);
                else if (idleAtHome(IDLE_TICKS)) skipped.clear();
                return;
            }
            target = found;
            world().claim(robot, target);
        }
        if (!robot.flyTowards(workPosition(target))) return;
        Progress progress = work(target);
        if (progress == Progress.WORKING) return;
        if (progress == Progress.FAILED) skipped.add(target);
        release();
    }

    private void rest() {
        robot.sleep(IDLE_TICKS);
        skipped.clear();
    }

    private boolean isTarget(BlockPos pos, WorkArea area) {
        Level level = level();
        if (!level.isLoaded(pos) || skipped.contains(pos) || !area.contains(pos)) return false;
        if (world().isClaimedByOther(robot, pos)) return false;
        return isWork(level, pos);
    }

    /**
     * Looks for the nearest work over several ticks, checking at most {@link #SCAN_BUDGET} positions a tick so a
     * large area (or one full of blocks protection refuses) never costs a whole sweep in one tick. Returns true once
     * the sweep is done, leaving its nearest find (or null) in {@link #scanBest}.
     */
    private boolean scanStep(WorkArea area) {
        if (scan == null || !area.equals(scanArea)) {
            scan = BlockPos.betweenClosed(area.min(), area.max()).iterator();
            scanArea = area;
            scanBest = null;
            scanBestDistance = Double.MAX_VALUE;
        }
        Vec3 from = robot.position();
        for (int checked = 0; checked < SCAN_BUDGET && scan.hasNext(); checked++) {
            BlockPos pos = scan.next();
            double distance = pos.distToCenterSqr(from);
            if (distance >= scanBestDistance || !isTarget(pos, area)) continue;
            scanBest = pos.immutable();
            scanBestDistance = distance;
        }
        if (scan.hasNext()) return false;
        scan = null;
        return true;
    }

    /** The first side of {@code pos}, top first, whose neighbour has no collision box (air, fluid or a plant). */
    @Nullable
    protected static Direction openSide(Level level, BlockPos pos) {
        for (Direction side : APPROACH) {
            BlockPos next = pos.relative(side);
            if (level.getBlockState(next).getCollisionShape(level, next).isEmpty()) return side;
        }
        return null;
    }

    /** Where the robot hovers while it works: just outside the block's open side (above it if none is open). */
    private Vec3 workPosition(BlockPos pos) {
        Direction side = openSide(level(), pos);
        if (side == null) side = Direction.UP;
        Vec3 normal = Vec3.atLowerCornerOf(side.getNormal());
        return Vec3.atCenterOf(pos).add(normal.scale(0.75)).subtract(0, RobotEntity.SIZE / 2, 0);
    }

    private void release() {
        if (target != null) world().release(robot, target);
        target = null;
    }

    // Items to work with.

    /** The first inventory slot holding a stack that passes {@code filter}, or -1. */
    protected int slotOf(Predicate<ItemStack> filter) {
        for (int slot = 0; slot < robot.inventory().getContainerSize(); slot++) {
            ItemStack stack = robot.inventory().getItem(slot);
            if (!stack.isEmpty() && filter.test(stack)) return slot;
        }
        return -1;
    }

    /**
     * True once the robot carries a stack that passes {@code usable}. Otherwise it takes home what else it carries,
     * then fetches up to a stack of usable items from the inventories beside its home station. Items that found no
     * work in the robot's last sweep are left there while it has others to try; with none there it rests.
     */
    protected boolean haveItems(Predicate<ItemStack> usable) {
        usesItems = true;
        Predicate<ItemStack> wanted = stack -> usable.test(stack) && !idleItems.contains(stack.getItem());
        if (slotOf(wanted) >= 0) return true;
        if (hasCargo()) {
            startUnloading(false);
            return false;
        }
        StationRef home = robot.home();
        if (home == null) {
            robot.sleep(IDLE_TICKS);
            return false;
        }
        Progress docking = robot.goDock(home);
        if (docking == Progress.WORKING) return false;
        DockingStationPluggable station = robot.dockedStation();
        ItemStack taken = docking == Progress.DONE && station != null ? station.extract(wanted, 64) : ItemStack.EMPTY;
        if (taken.isEmpty()) {
            idleItems.clear();
            rest();
            return false;
        }
        ItemStack left = robot.addToInventory(taken);
        if (!left.isEmpty()) robot.spawnAtLocation(left);
        return false;
    }

    @Override
    public void reset() {
        release();
        unloading = false;
        restAfterUnloading = false;
        scan = null;
        scanBest = null;
    }
}
