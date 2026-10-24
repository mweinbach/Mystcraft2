package ports.buildcraft.robotics.board;

import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.robotics.DockingStationPluggable;
import ports.buildcraft.robotics.Progress;
import ports.buildcraft.robotics.RobotEntity;

/**
 * Boards that break blocks, written for this port: the lumberjack cuts logs with an axe, the miner digs ores open to
 * air (or fluid) with a pickaxe, the breaker digs open stone with a pickaxe, the shovelman digs open dirt, sand,
 * gravel and the like with a shovel, the harvester picks mature crops and the leaf cutter cuts leaves (with shears
 * when its home has some). A board that needs a tool takes it from the inventories beside its home station first.
 * The robot breaks each block as its owner (protection may refuse; see {@link RobotEntity#breakBlock}) and brings
 * what it collects home once it carries a full load or runs out of work. A robot with no tool to fetch, or nothing to
 * break, rests at home.
 */
public class BreakerBoard extends BlockBoard {
    private static final List<TagKey<Block>> ORE_TAGS = List.of(
            tag("c", "ores"), tag("forge", "ores"), BlockTags.COAL_ORES, BlockTags.IRON_ORES, BlockTags.COPPER_ORES,
            BlockTags.GOLD_ORES, BlockTags.REDSTONE_ORES, BlockTags.LAPIS_ORES, BlockTags.DIAMOND_ORES, BlockTags.EMERALD_ORES);

    private final Predicate<BlockState> blocks;
    /** The tool the board needs, or null if it breaks by hand. */
    @Nullable
    private final Predicate<ItemStack> tools;
    /** A tool a hand-breaking board takes from home when there is one, or null. */
    @Nullable
    private final Predicate<ItemStack> optionalTools;
    private final boolean mustBeExposed;

    protected BreakerBoard(RobotEntity robot, Predicate<BlockState> blocks, @Nullable Predicate<ItemStack> tools,
            @Nullable Predicate<ItemStack> optionalTools, boolean mustBeExposed) {
        super(robot);
        this.blocks = blocks;
        this.tools = tools;
        this.optionalTools = optionalTools;
        this.mustBeExposed = mustBeExposed;
    }

    public static BreakerBoard lumberjack(RobotEntity robot) {
        return new BreakerBoard(robot, BreakerBoard::isLog, BreakerBoard::isAxe, null, false);
    }

    public static BreakerBoard miner(RobotEntity robot) {
        return new BreakerBoard(robot, BreakerBoard::isOre, BreakerBoard::isPickaxe, null, true);
    }

    public static BreakerBoard breaker(RobotEntity robot) {
        return new BreakerBoard(robot, BreakerBoard::isStone, BreakerBoard::isPickaxe, null, true);
    }

    public static BreakerBoard shovelman(RobotEntity robot) {
        return new BreakerBoard(robot, BreakerBoard::isShovelGround, BreakerBoard::isShovel, null, true);
    }

    public static BreakerBoard harvester(RobotEntity robot) {
        return new BreakerBoard(robot, BreakerBoard::isMatureCrop, null, null, false);
    }

    public static BreakerBoard leafCutter(RobotEntity robot) {
        return new BreakerBoard(robot, BreakerBoard::isLeaves, null, BreakerBoard::isShears, false);
    }

    private static TagKey<Block> tag(String namespace, String path) {
        return TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation(namespace, path));
    }

    // What each board breaks.

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

    /** Natural stone of the overworld and the nether, and cobblestone. */
    public static boolean isStone(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.COBBLED_DEEPSLATE);
    }

    /** Natural ground a shovel digs: dirt of every kind but farmland, sand, gravel, clay, soul sand and soil, mud and snow. */
    public static boolean isShovelGround(BlockState state) {
        if (!state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return false;
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.MUD)
                || state.is(Blocks.SNOW_BLOCK);
    }

    /** Crops at their last age (wheat, carrots, potatoes, beetroot and modded crops), ripe nether wart and cocoa. */
    public static boolean isMatureCrop(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) return crop.isMaxAge(state);
        if (block instanceof NetherWartBlock) return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        if (block instanceof CocoaBlock) return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        return false;
    }

    public static boolean isLeaves(BlockState state) {
        return state.is(BlockTags.LEAVES);
    }

    // The tools.

    public static boolean isAxe(ItemStack stack) {
        return !stack.isEmpty() && stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()) > 1;
    }

    public static boolean isPickaxe(ItemStack stack) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        return !stack.isEmpty() && stack.getDestroySpeed(stone) > 1 && stack.isCorrectToolForDrops(stone);
    }

    public static boolean isShovel(ItemStack stack) {
        return !stack.isEmpty() && stack.getDestroySpeed(Blocks.DIRT.defaultBlockState()) > 1;
    }

    public static boolean isShears(ItemStack stack) {
        return stack.getItem() instanceof ShearsItem;
    }

    @Override
    protected boolean prepare() {
        if (tools != null) return haveTool(tools);
        if (optionalTools != null && robot.tool().isEmpty()) takeOptionalTool(optionalTools);
        return true;
    }

    /** Takes a tool the board can do without from beside home, but only while the robot is docked there anyway. */
    private void takeOptionalTool(Predicate<ItemStack> filter) {
        DockingStationPluggable station = robot.dockedStation();
        if (station == null || !station.ref().equals(robot.home())) return;
        ItemStack tool = station.extract(filter, 1);
        if (!tool.isEmpty()) robot.setTool(tool);
    }

    @Override
    protected boolean isWork(Level level, BlockPos pos) {
        return blocks.test(level.getBlockState(pos)) && (!mustBeExposed || openSide(level, pos) != null);
    }

    @Override
    protected Progress work(BlockPos pos) {
        return robot.breakBlock(pos);
    }
}
