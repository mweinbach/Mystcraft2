package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.require;
import static ports.buildcraft.gametest.BCTests.tile;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import ports.buildcraft.builders.BCBuilders;
import ports.buildcraft.builders.FillerBlock;
import ports.buildcraft.builders.FillerBlockEntity;
import ports.buildcraft.builders.FillerMenu;
import ports.buildcraft.builders.filler.FilledTemplate;
import ports.buildcraft.builders.filler.FillerParameter;
import ports.buildcraft.builders.filler.FillerPatterns;
import ports.buildcraft.builders.filler.Pattern;
import ports.buildcraft.builders.filler.PatternParameterAxis;
import ports.buildcraft.builders.filler.PatternParameterHollow;
import ports.buildcraft.builders.filler.PatternStatement;
import ports.buildcraft.core.BCCore;
import ports.buildcraft.core.marker.volume.Lock;
import ports.buildcraft.core.marker.volume.VolumeBox;
import ports.buildcraft.core.marker.volume.VolumeBoxes;

/** Loader-neutral GameTest bodies for the filler, its patterns and the template builder. */
public final class FillerTests {
    private static final BlockPos BOX_MIN = new BlockPos(4, 2, 4);
    private static final BlockPos BOX_MAX = new BlockPos(6, 4, 6);
    private static final BlockPos BOX_CENTRE = new BlockPos(5, 3, 5);
    /** West of {@link #BOX_MIN} and facing west, so the box is behind it. */
    private static final BlockPos FILLER = new BlockPos(3, 2, 4);

    private FillerTests() {}

    static FillerBlockEntity placeFiller(GameTestHelper helper, BlockPos pos, Direction facing) {
        BlockState state = BCBuilders.FILLER.get().defaultBlockState().setValue(FillerBlock.FACING, facing);
        helper.setBlock(pos, state);
        BCBuilders.FILLER.get().setPlacedBy(helper.getLevel(), helper.absolutePos(pos), state, null, ItemStack.EMPTY);
        return tile(helper, pos, FillerBlockEntity.class);
    }

    /** A 3x3x3 volume box at {@link #BOX_MIN} to {@link #BOX_MAX}, taken by a filler placed against it. */
    static FillerBlockEntity fillerOnVolumeBox(GameTestHelper helper) {
        VolumeBox volumeBox = BCTests.placeVolumeBox(helper, helper.makeMockPlayer(), BOX_MIN.below());
        volumeBox.box.extendToEncompass(helper.absolutePos(BOX_MAX));
        return placeFiller(helper, FILLER, Direction.WEST);
    }

    static void removeVolumeBoxes(GameTestHelper helper) {
        VolumeBoxes volumeBoxes = VolumeBoxes.get(helper.getLevel());
        volumeBoxes.volumeBoxes.removeIf(box -> box.box.contains(helper.absolutePos(BOX_MIN)));
        volumeBoxes.markDirty();
    }

    static void setPattern(FillerBlockEntity filler, Pattern pattern) {
        require(null, filler.handleButton(FillerBlockEntity.patternButton(FillerPatterns.indexOf(pattern))), "the filler must accept " + pattern);
    }

    static List<BlockPos> boxPositions() {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos.betweenClosed(BOX_MIN, BOX_MAX).forEach(pos -> positions.add(pos.immutable()));
        return positions;
    }

    static void requireTemplate(Pattern pattern, BlockPos size, int expected) {
        PatternStatement statement = new PatternStatement();
        statement.set(pattern);
        FilledTemplate template = pattern.createTemplate(size, statement.parameters());
        int count = template == null ? -1 : template.count();
        require(null, count == expected, pattern + " at " + size.toShortString() + " must fill " + expected + " blocks, got " + count);
    }

    /**
     * The simple patterns fill upstream's shapes; every pattern makes a template of the box's size for any
     * value of each of its parameters, and "none" makes none.
     */
    public static void fillerPatternsMakeTemplates(GameTestHelper helper) {
        BlockPos cube = new BlockPos(3, 3, 3);
        requireTemplate(FillerPatterns.CLEAR, cube, 0);
        requireTemplate(FillerPatterns.FILL, cube, 27);
        requireTemplate(FillerPatterns.BOX, cube, 26);
        requireTemplate(FillerPatterns.FRAME, cube, 20);
        requireTemplate(FillerPatterns.FRAME, new BlockPos(4, 4, 4), 32);
        requireTemplate(FillerPatterns.PYRAMID, new BlockPos(5, 3, 5), 25 + 9 + 1);
        requireTemplate(FillerPatterns.STAIRS, cube, 9 + 6 + 3);
        requireTemplate(FillerPatterns.NONE, cube, -1);

        List<BlockPos> sizes = List.of(new BlockPos(1, 1, 1), new BlockPos(2, 3, 4), new BlockPos(5, 5, 5), new BlockPos(7, 2, 3), new BlockPos(1, 6, 9));
        for (Pattern pattern : FillerPatterns.PATTERNS) {
            PatternStatement statement = new PatternStatement();
            statement.set(pattern);
            FillerParameter[] defaults = statement.parameters();
            for (BlockPos size : sizes) {
                List<FillerParameter[]> variants = new ArrayList<>();
                variants.add(defaults);
                for (int i = 0; i < defaults.length; i++) {
                    if (defaults[i] == null) continue;
                    for (FillerParameter value : defaults[i].allValues()) {
                        FillerParameter[] variant = defaults.clone();
                        variant[i] = value;
                        variants.add(variant);
                    }
                }
                for (FillerParameter[] params : variants) {
                    FilledTemplate template = pattern.createTemplate(size, params);
                    if (pattern == FillerPatterns.NONE) {
                        require(helper, template == null, "the none pattern must not make a template");
                        continue;
                    }
                    require(helper, template != null && template.getSize().equals(size) && template.count() <= size.getX() * size.getY() * size.getZ(),
                            pattern + " must make a template of size " + size.toShortString());
                }
            }
        }
        helper.succeed();
    }

    /** Changing the pattern keeps parameters that still have the right type and resets the rest; the statement survives a save. */
    public static void patternStatementKeepsParameters(GameTestHelper helper) {
        PatternStatement statement = new PatternStatement();
        require(helper, statement.get() == FillerPatterns.NONE && statement.get(0) == null, "a new statement has no pattern");
        statement.set(FillerPatterns.CIRCLE);
        require(helper, statement.get(0) == PatternParameterAxis.Y && statement.get(1) == PatternParameterHollow.HOLLOW,
                "a 2D shape starts on the Y axis, hollow");
        require(helper, statement.canSet(0, PatternParameterAxis.X) && !statement.canSet(0, PatternParameterHollow.FILLED_OUTER),
                "a slot only takes parameters of its type");
        statement.set(0, PatternParameterAxis.X);
        statement.set(1, PatternParameterHollow.FILLED_OUTER);
        statement.set(FillerPatterns.TRIANGLE);
        require(helper, statement.get(0) == PatternParameterAxis.X && statement.get(1) == PatternParameterHollow.FILLED_OUTER,
                "switching between 2D shapes must keep the axis and fill");
        PatternStatement loaded = new PatternStatement();
        loaded.load(statement.save());
        require(helper, loaded.equals(statement), "the statement must survive a save");
        statement.set(FillerPatterns.SPHERE);
        require(helper, statement.get(0) == PatternParameterHollow.FILLED_INNER && statement.get(1) == null,
                "a sphere replaces the axis with its own fill and drops the rest");
        statement.set(FillerPatterns.FILL);
        require(helper, statement.get(0) == null, "fill has no parameters");
        helper.succeed();
    }

    /**
     * A filler placed against a volume box copies the box and locks it against removal and resizing, showing
     * write stripes; once the filler is gone the lock lapses and its resources drop.
     */
    public static void fillerTakesVolumeBox(GameTestHelper helper) {
        FillerBlockEntity filler = fillerOnVolumeBox(helper);
        VolumeBox volumeBox = VolumeBoxes.get(helper.getLevel()).getVolumeBoxAt(helper.absolutePos(BOX_MIN));
        require(helper, volumeBox != null, "the volume box must stay");
        require(helper, filler.hasBox() && filler.box.min().equals(helper.absolutePos(BOX_MIN)) && filler.box.max().equals(helper.absolutePos(BOX_MAX)),
                "the filler must copy the volume box, got " + filler.box.min() + " to " + filler.box.max());
        require(helper, !filler.markerBox, "a volume box draws its own stripes, so the filler must not");
        require(helper, volumeBox.isLockedFor(Lock.Remove.class) && volumeBox.isLockedFor(Lock.Resize.class)
                && volumeBox.getLockTargetsStream().anyMatch(new Lock.UsedByMachine(Lock.MachineUse.STRIPES_WRITE)::equals),
                "the filler must lock the box and mark it as written");
        filler.setItem(0, new ItemStack(Items.STONE, 5));
        helper.setBlock(FILLER, Blocks.AIR);
        VolumeBoxes.get(helper.getLevel()).tick();
        require(helper, volumeBox.locks.isEmpty(), "the lock must lapse once the filler is gone");
        require(helper, !helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(FILLER)).inflate(1),
                item -> item.getItem().is(Items.STONE)).isEmpty(), "a broken filler must drop its resources");
        removeVolumeBoxes(helper);
        helper.succeed();
    }

    /** A filler placed against a land mark box takes the marks' area and removes the marks, drawing the stripes itself. */
    public static void fillerTakesLandMarkArea(GameTestHelper helper) {
        BCTests.landMarkBox(helper, new BlockPos(2, 1, 1), new BlockPos(6, 1, 1), new BlockPos(2, 1, 5));
        FillerBlockEntity filler = placeFiller(helper, new BlockPos(1, 1, 1), Direction.WEST);
        require(helper, filler.box.min().equals(helper.absolutePos(new BlockPos(2, 1, 1))) && filler.box.max().equals(helper.absolutePos(new BlockPos(6, 1, 5))),
                "the filler must take the marks' box as it is, got " + filler.box.min() + " to " + filler.box.max());
        require(helper, filler.markerBox, "the filler draws stripes around a marked area");
        for (BlockPos mark : List.of(new BlockPos(2, 1, 1), new BlockPos(6, 1, 1), new BlockPos(2, 1, 5))) {
            require(helper, helper.getBlockState(mark).isAir(), "the filler must remove the land mark at " + mark);
        }
        FillerBlockEntity lone = placeFiller(helper, new BlockPos(7, 1, 7), Direction.NORTH);
        require(helper, !lone.hasBox() && lone.getBuilder() == null, "a filler with nothing behind it has no area");
        helper.succeed();
    }

    /**
     * The menu only takes block items and its buttons pick patterns and toggles; inverting swaps filled and
     * empty blocks; the settings, area and resources survive a save.
     */
    public static void fillerSettingsAndSaving(GameTestHelper helper) {
        FillerBlockEntity filler = fillerOnVolumeBox(helper);
        Player player = helper.makeMockPlayer();
        FillerMenu menu = new FillerMenu(0, player.getInventory(), filler);
        require(helper, !menu.getSlot(36).mayPlace(new ItemStack(Items.STICK)) && menu.getSlot(36).mayPlace(new ItemStack(Items.STONE)),
                "resource slots only take block items");
        require(helper, !filler.canTakeItemThroughFace(0, new ItemStack(Items.STONE), Direction.UP), "resources cannot be pulled out");
        require(helper, filler.getBuilder() == null, "without a pattern the filler has nothing to build");

        require(helper, menu.clickMenuButton(player, FillerBlockEntity.patternButton(FillerPatterns.indexOf(FillerPatterns.FILL))), "the pattern button must work");
        require(helper, filler.patternStatement.get() == FillerPatterns.FILL && filler.template() != null && filler.template().count() == 27,
                "fill must fill the whole box");
        require(helper, filler.handleButton(FillerBlockEntity.BUTTON_INVERT + 1) && filler.isInverted() && filler.template().count() == 0,
                "inverted fill must leave the box empty");
        setPattern(filler, FillerPatterns.BOX);
        require(helper, filler.template().count() == 1 && filler.template().get(1, 1, 1), "an inverted box must fill only its centre");
        setPattern(filler, FillerPatterns.CIRCLE);
        require(helper, filler.handleButton(FillerBlockEntity.parameterButton(1, PatternParameterHollow.FILLED_OUTER))
                && filler.patternStatement.get(1) == PatternParameterHollow.FILLED_OUTER, "the parameter button must set the parameter");
        require(helper, !filler.handleButton(FillerBlockEntity.BUTTON_PARAMETER + PatternParameterAxis.values().length)
                && !filler.handleButton(FillerBlockEntity.BUTTON_PARAMETER + 300) && filler.patternStatement.get(0) == PatternParameterAxis.Y,
                "values beyond the parameter's type and slots the pattern does not use must be refused");
        require(helper, filler.handleButton(FillerBlockEntity.BUTTON_EXCAVATE) && !filler.canExcavateSetting(), "the excavate button must turn excavation off");
        filler.setItem(3, new ItemStack(Items.BRICKS, 12));

        CompoundTag saved = filler.saveWithoutMetadata();
        FillerBlockEntity loaded = new FillerBlockEntity(helper.absolutePos(FILLER), filler.getBlockState());
        loaded.load(saved);
        require(helper, loaded.box.min().equals(filler.box.min()) && loaded.box.max().equals(filler.box.max()) && !loaded.markerBox,
                "saving must keep the area");
        require(helper, loaded.patternStatement.equals(filler.patternStatement) && loaded.isInverted() && !loaded.canExcavateSetting(),
                "saving must keep the pattern and toggles");
        require(helper, loaded.getItem(3).is(Items.BRICKS) && loaded.getItem(3).getCount() == 12, "saving must keep the resources");
        require(helper, loaded.template() != null && loaded.template().count() == filler.template().count(), "loading must rebuild the template");
        CompoundTag update = filler.getUpdateTag();
        require(helper, update.contains("box") && update.contains("patternStatement") && update.getBoolean("inverted"),
                "clients must receive the area, pattern and toggles");
        removeVolumeBoxes(helper);
        helper.succeed();
    }

    /** With power and stone the filler clears what is in the way, then builds the box pattern from its resources. */
    public static void fillerBuildsBox(GameTestHelper helper) {
        FillerBlockEntity filler = fillerOnVolumeBox(helper);
        helper.setBlock(BOX_CENTRE, Blocks.DIRT);
        setPattern(filler, FillerPatterns.BOX);
        filler.setItem(0, new ItemStack(Items.STONE, 64));
        helper.onEachTick(() -> filler.battery().set(filler.battery().capacity()));
        helper.succeedWhen(() -> {
            require(helper, helper.getBlockState(BOX_CENTRE).isAir(), "the filler must clear the box's centre");
            for (BlockPos pos : boxPositions()) {
                if (pos.equals(BOX_CENTRE)) continue;
                require(helper, helper.getBlockState(pos).is(Blocks.STONE), "the box's shell needs stone at " + pos);
            }
            require(helper, filler.getItem(0).getCount() == 64 - 26, "each placed block must use one stone, " + filler.getItem(0).getCount() + " left");
            require(helper, !filler.getBuilder().hasTasks(), "a finished box leaves no tasks");
            removeVolumeBoxes(helper);
        });
    }

    /** The clear pattern only breaks blocks while excavation is on; nothing it breaks needs resources. */
    public static void fillerClearsOnlyWhenExcavating(GameTestHelper helper) {
        FillerBlockEntity filler = fillerOnVolumeBox(helper);
        Block[] palette = {Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.GLASS};
        List<BlockPos> positions = boxPositions();
        for (int i = 0; i < positions.size(); i++) helper.setBlock(positions.get(i), palette[i % palette.length]);
        require(helper, filler.handleButton(FillerBlockEntity.BUTTON_EXCAVATE), "the excavate button must work");
        setPattern(filler, FillerPatterns.CLEAR);
        helper.onEachTick(() -> filler.battery().set(filler.battery().capacity()));
        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    for (BlockPos pos : positions) require(helper, !helper.getBlockState(pos).isAir(), "without excavation nothing may break, " + pos + " did");
                    require(helper, filler.getCountToBreak() == 0, "without excavation nothing is left to break");
                    require(helper, filler.handleButton(FillerBlockEntity.BUTTON_EXCAVATE + 1), "the excavate button must turn excavation back on");
                })
                .thenWaitUntil(() -> {
                    for (BlockPos pos : positions) require(helper, helper.getBlockState(pos).isAir(), "the filler must clear " + pos);
                    require(helper, filler.getCountToBreak() == 0 && filler.getCountToPlace() == 0, "a cleared box leaves nothing to do");
                })
                .thenExecute(() -> removeVolumeBoxes(helper))
                .thenSucceed();
    }
}
