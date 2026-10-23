package ports.buildcraft.robotics.board;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.robotics.DockingStationPluggable;
import ports.buildcraft.robotics.Progress;
import ports.buildcraft.robotics.RobotEntity;
import ports.buildcraft.robotics.StationRef;
import ports.buildcraft.robotics.WorkArea;

/**
 * Boards that break blocks with a tool, written for this port: the lumberjack cuts logs with an axe and the miner
 * digs ores that lie open to air (or fluid) with a pickaxe. The robot takes its tool from the inventories beside its
 * home station, flies to the nearest matching block in its work area, breaks it as its owner (protection may refuse;
 * see {@link RobotEntity#breakBlock}) and brings what it collects home once it carries a full load or runs out of
 * work. A robot with no tool to fetch, or nothing to break, rests at home.
 */
public class BreakerBoard extends RobotBoard {
    public static final int IDLE_TICKS = 100;
    private static final List<TagKey<Block>> ORE_TAGS = List.of(
            tag("c", "ores"), tag("forge", "ores"), BlockTags.COAL_ORES, BlockTags.IRON_ORES, BlockTags.COPPER_ORES,
            BlockTags.GOLD_ORES, BlockTags.REDSTONE_ORES, BlockTags.LAPIS_ORES, BlockTags.DIAMOND_ORES, BlockTags.EMERALD_ORES);
    private static final Direction[] APPROACH = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN};

    private final Predicate<BlockState> blocks;
    private final Predicate<ItemStack> tools;
    private final boolean mustBeExposed;

    @Nullable
    private BlockPos target;
    private boolean unloading;
    /** Blocks the robot failed to break (protected, or its tool cannot harvest them), skipped until a scan finds nothing else. */
    private final Set<BlockPos> skipped = new HashSet<>();
    /** Positions checked per tick while looking for a target: a 64-block cube takes 32 ticks, the default area 5. */
    public static final int SCAN_BUDGET = 8192;
    /** The sweep in progress over {@link #scanArea}, and the nearest target it has found so far. */
    @Nullable
    private Iterator<BlockPos> scan;
    @Nullable
    private WorkArea scanArea;
    @Nullable
    private BlockPos scanBest;
    private double scanBestDistance;

    protected BreakerBoard(RobotEntity robot, Predicate<BlockState> blocks, Predicate<ItemStack> tools, boolean mustBeExposed) {
        super(robot);
        this.blocks = blocks;
        this.tools = tools;
        this.mustBeExposed = mustBeExposed;
    }

    public static BreakerBoard lumberjack(RobotEntity robot) {
        return new BreakerBoard(robot, BreakerBoard::isLog, BreakerBoard::isAxe, false);
    }

    public static BreakerBoard miner(RobotEntity robot) {
        return new BreakerBoard(robot, BreakerBoard::isOre, BreakerBoard::isPickaxe, true);
    }

    private static TagKey<Block> tag(String namespace, String path) {
        return TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation(namespace, path));
    }

    public static boolean isLog(BlockState state) {
        return state.is(BlockTags.LOGS);
    }

    public static boolean isOre(BlockState state) {
        if (state.is(Blocks.NETHER_QUARTZ_ORE)) return true;
        for (TagKey<Block> tag : ORE_TAGS) {
            if (state.is(tag)) return true;
        }
        return false;
    }

    public static boolean isAxe(ItemStack stack) {
        return !stack.isEmpty() && stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()) > 1;
    }

    public static boolean isPickaxe(ItemStack stack) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        return !stack.isEmpty() && stack.getDestroySpeed(stone) > 1 && stack.isCorrectToolForDrops(stone);
    }

    @Override
    public void tick() {
        if (unloading) {
            Progress progress = unloadAtHome();
            if (progress == Progress.WORKING) return;
            unloading = false;
            if (progress == Progress.FAILED) robot.sleep(IDLE_TICKS);
            return;
        }
        if (robot.isInventoryFull()) {
            unloading = true;
            return;
        }
        if (!robot.hasTool(tools)) {
            fetchTool();
            return;
        }
        WorkArea area = robot.workArea();
        if (target == null || !isTarget(target, area)) {
            release();
            if (!scanStep(area)) return;
            BlockPos found = scanBest;
            scanBest = null;
            if (found != null && !isTarget(found, area)) return; // changed during the sweep: look again
            if (found == null) {
                if (!robot.isInventoryEmpty()) {
                    unloading = true;
                    return;
                }
                if (idleAtHome(IDLE_TICKS)) skipped.clear();
                return;
            }
            target = found;
            world().claim(robot, target);
        }
        if (!robot.flyTowards(workPosition(target))) return;
        Progress progress = robot.breakBlock(target);
        if (progress == Progress.WORKING) return;
        if (progress == Progress.FAILED) skipped.add(target);
        release();
    }

    /** Puts away a tool that does not suit this board, then takes one that does from home. */
    private void fetchTool() {
        if (!robot.tool().isEmpty()) {
            ItemStack left = robot.addToInventory(robot.tool());
            robot.setTool(left);
            if (!left.isEmpty()) unloading = true;
            return;
        }
        StationRef home = robot.home();
        if (home == null) {
            robot.sleep(IDLE_TICKS);
            return;
        }
        Progress docking = robot.goDock(home);
        if (docking == Progress.WORKING) return;
        DockingStationPluggable station = robot.dockedStation();
        ItemStack tool = docking == Progress.DONE && station != null ? station.extract(tools, 1) : ItemStack.EMPTY;
        if (!tool.isEmpty()) {
            robot.setTool(tool);
        } else if (!robot.isInventoryEmpty()) {
            unloading = true;
        } else {
            robot.sleep(IDLE_TICKS);
        }
    }

    private boolean isTarget(BlockPos pos, WorkArea area) {
        Level level = level();
        if (!level.isLoaded(pos) || skipped.contains(pos) || !area.contains(pos)) return false;
        if (world().isClaimedByOther(robot, pos)) return false;
        BlockState state = level.getBlockState(pos);
        return blocks.test(state) && (!mustBeExposed || openSide(level, pos) != null);
    }

    /**
     * Looks for the nearest target over several ticks, checking at most {@link #SCAN_BUDGET} positions a tick so a
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

    @Nullable
    private static Direction openSide(Level level, BlockPos pos) {
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

    @Override
    public void reset() {
        release();
        unloading = false;
        scan = null;
        scanBest = null;
    }
}
