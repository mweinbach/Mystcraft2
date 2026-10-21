package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.chest;
import static ports.buildcraft.gametest.BCTests.count;
import static ports.buildcraft.gametest.BCTests.pipe;
import static ports.buildcraft.gametest.BCTests.require;
import static ports.buildcraft.gametest.BCTests.tile;
import static ports.buildcraft.gametest.BCTests.useOn;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.silicon.AssemblyTableBlockEntity;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.silicon.plug.LensPluggable;
import ports.buildcraft.silicon.plug.LensVariant;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlock;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.SideCheck;
import ports.buildcraft.transport.flow.ItemFlow;

/** Loader-neutral GameTests of lenses and filters. */
public final class LensTests {
    private static final LensVariant RED_LENS = new LensVariant(DyeColor.RED, false);
    private static final LensVariant RED_FILTER = new LensVariant(DyeColor.RED, true);
    private static final LensVariant BLUE_FILTER = new LensVariant(DyeColor.BLUE, true);
    private static final LensVariant CLEAR_LENS = new LensVariant(null, false);
    private static final LensVariant CLEAR_FILTER = new LensVariant(null, true);
    private static final double SPEED = 0.25;
    private static final BlockPos TABLE = new BlockPos(5, 2, 6);

    private LensTests() {}

    private static LensPluggable lens(PipeBlockEntity pipe, Direction side, LensVariant variant) {
        LensPluggable lens = new LensPluggable(BCSilicon.LENS, pipe, side, variant);
        pipe.replacePluggable(side, lens);
        return lens;
    }

    private static ItemFlow items(PipeBlockEntity pipe) {
        return (ItemFlow) pipe.flow();
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents text ? text.getKey() : component.getString();
    }

    private static String argKey(Component component) {
        return component.getContents() instanceof TranslatableContents text && text.getArgs().length == 1
                && text.getArgs()[0] instanceof Component arg ? key(arg) : "";
    }

    /** The faces {@code plug}'s side check puts first, for an item of {@code colour}. */
    private static EnumSet<Direction> first(LensPluggable plug, @Nullable DyeColor colour) {
        SideCheck check = new SideCheck(ItemStack.EMPTY, colour, null);
        plug.itemSideCheck(check);
        return check.order().isEmpty() ? EnumSet.noneOf(Direction.class) : check.order().get(0);
    }

    /** The colour of the first item travelling in {@code flow}, as saved, as reloaded and as sent to clients. */
    private static List<DyeColor> savedColours(PipeBlockEntity pipe) {
        CompoundTag saved = new CompoundTag();
        items(pipe).save(saved);
        List<DyeColor> colours = new ArrayList<>();
        int stored = saved.getList("items", Tag.TAG_COMPOUND).getCompound(0).getByte("colour");
        colours.add(stored <= 0 ? null : DyeColor.byId(stored - 1));
        ItemFlow loaded = new ItemFlow(pipe);
        loaded.load(saved);
        loaded.onLevelSet();
        CompoundTag update = new CompoundTag();
        loaded.writeClient(update);
        ItemFlow client = new ItemFlow(pipe);
        client.readClient(update);
        colours.add(client.clientItems().isEmpty() ? null : client.clientItems().get(0).item().colour());
        return colours;
    }

    /**
     * The assembly table makes lenses from glass and filters from glass and iron bars; any glass makes clear ones.
     * Lenses and filters are named after their colour, or "Clear". They only go on item pipes, are placed by
     * right-clicking a face, stay open to connections and add their box, 0 to 2 pixels in from the face, to the pipe's
     * shape. Clients get the colour and kind, saving keeps them, and taking one off drops it. A filter refuses items of
     * other colours, puts its own colour first and other colours nowhere, and puts unpainted items last; a lens paints
     * items crossing its face and a clear lens washes them.
     */
    public static void lensPlacesSyncsAndSaves(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 2, 2), b = new BlockPos(3, 2, 2), fluid = new BlockPos(2, 2, 5);
        PipeBlockEntity pipe = pipe(helper, a, BCTransport.STONE_ITEM);
        pipe(helper, b, BCTransport.STONE_ITEM);
        pipe(helper, fluid, BCTransport.STONE_FLUID);
        Player player = helper.makeMockPlayer();
        BlockPos absolute = helper.absolutePos(a);

        ItemStack filters = new ItemStack(BCSilicon.lensItem(RED_FILTER), 2);
        ItemStack clear = new ItemStack(BCSilicon.lensItem(CLEAR_LENS));
        require(helper, key(filters.getHoverName()).equals("item.buildcraft.filter") && argKey(filters.getHoverName()).equals("color.minecraft.red")
                && key(clear.getHoverName()).equals("item.buildcraft.lens") && argKey(clear.getHoverName()).equals("color.clear"),
                "lenses and filters are named after their colour, or clear");
        require(helper, LensVariant.VALUES.size() == 34 && BCSilicon.LENS_ITEMS.size() == 34, "upstream's 34 lenses and filters are each an item");

        helper.setBlock(TABLE, BCSilicon.ASSEMBLY_TABLE.get());
        AssemblyTableBlockEntity table = tile(helper, TABLE, AssemblyTableBlockEntity.class);
        table.setItem(0, new ItemStack(Items.RED_STAINED_GLASS));
        table.setItem(1, new ItemStack(Items.IRON_BARS));

        helper.runAfterDelay(2, () -> {
            Set<Item> offered = new HashSet<>();
            for (AssemblyTableBlockEntity.Instruction instruction : table.recipesStates().keySet()) offered.add(instruction.output.getItem());
            require(helper, offered.equals(Set.of(BCSilicon.lensItem(RED_LENS), BCSilicon.lensItem(RED_FILTER), BCSilicon.lensItem(CLEAR_LENS),
                    BCSilicon.lensItem(CLEAR_FILTER))), "red stained glass and iron bars make red and, as any glass, clear lenses and filters");
            require(helper, useOn(helper, player, fluid, Direction.EAST, filters) == InteractionResult.PASS && filters.getCount() == 2,
                    "lenses only go on item pipes");
            require(helper, useOn(helper, player, a, Direction.EAST, filters) == InteractionResult.CONSUME && filters.getCount() == 1,
                    "right-clicking an item pipe's face with a filter places it and uses one item");
            require(helper, pipe.getPluggable(Direction.EAST) instanceof LensPluggable placed && placed.variant().equals(RED_FILTER),
                    "the placed filter keeps its colour and kind");
            lens(pipe, Direction.WEST, CLEAR_LENS);
        });
        helper.runAfterDelay(4, () -> {
            require(helper, pipe.isConnected(Direction.EAST) && !pipe.isBlocked(Direction.EAST), "a filter leaves its face open");
            require(helper, PipeBlock.pluggableAt(pipe, Vec3.atLowerCornerOf(absolute).add(15 / 16.0, 3.5 / 16, 12.5 / 16)) == Direction.EAST
                    && PipeBlock.pluggableAt(pipe, Vec3.atLowerCornerOf(absolute).add(13.5 / 16, 0.5, 0.5)) == null,
                    "the filter's box spans 0 to 2 pixels in from its face and 3 to 13 across it");
            LensPluggable filter = (LensPluggable) pipe.getPluggable(Direction.EAST);
            require(helper, filter.getPickStack().getItem() == BCSilicon.lensItem(RED_FILTER), "a filter picks as its own item");

            CompoundTag update = pipe.getUpdateTag().getCompound("plugs");
            CompoundTag east = update.getCompound("east");
            require(helper, east.getString("id").equals("buildcraft:lens") && east.getCompound("data").getString("colour").equals("red")
                    && east.getCompound("data").getBoolean("f") && !update.getCompound("west").getCompound("data").contains("colour"),
                    "clients get each lens's colour and kind, got " + update);
            LensPluggable client = new LensPluggable(BCSilicon.LENS, pipe, Direction.EAST);
            client.readClient(east.getCompound("data"));
            require(helper, client.variant().equals(RED_FILTER), "a client's lens takes the colour and kind");

            PipeBlockEntity loaded = new PipeBlockEntity(absolute, helper.getBlockState(a));
            loaded.load(pipe.saveWithoutMetadata());
            require(helper, loaded.getPluggable(Direction.EAST) instanceof LensPluggable saved && saved.variant().equals(RED_FILTER)
                    && loaded.getPluggable(Direction.WEST) instanceof LensPluggable washed && washed.variant().equals(CLEAR_LENS),
                    "saving keeps each lens's colour and kind");

            require(helper, !filter.canItemEnter(DyeColor.BLUE) && filter.canItemEnter(DyeColor.RED) && filter.canItemEnter(null),
                    "a filter only lets in its own colour and unpainted items");
            require(helper, filter.colourItemThrough(DyeColor.BLUE) == DyeColor.BLUE && filter.colourItemThrough(null) == null,
                    "a filter leaves colours alone");
            require(helper, first(filter, DyeColor.RED).equals(EnumSet.of(Direction.EAST)) && !first(filter, DyeColor.BLUE).contains(Direction.EAST)
                    && first(filter, null).size() == 5, "a filter puts its colour first, other colours nowhere and unpainted items last");
            LensPluggable clearFilter = new LensPluggable(BCSilicon.LENS, pipe, Direction.UP, CLEAR_FILTER);
            require(helper, first(clearFilter, null).equals(EnumSet.of(Direction.UP)) && !clearFilter.canItemEnter(DyeColor.RED),
                    "a clear filter is the filter for unpainted items");
            LensPluggable red = new LensPluggable(BCSilicon.LENS, pipe, Direction.UP, RED_LENS);
            LensPluggable washer = (LensPluggable) pipe.getPluggable(Direction.WEST);
            require(helper, red.colourItemThrough(null) == DyeColor.RED && red.colourItemThrough(DyeColor.BLUE) == DyeColor.RED
                    && washer.colourItemThrough(DyeColor.BLUE) == null && red.canItemEnter(DyeColor.BLUE) && first(red, DyeColor.BLUE).size() == 6,
                    "a lens paints whatever crosses it and a clear lens washes it, letting anything in and steering nothing");

            List<ItemStack> drops = new ArrayList<>();
            pipe.removePluggable(Direction.EAST, drops);
            require(helper, drops.size() == 1 && drops.get(0).getItem() == BCSilicon.lensItem(RED_FILTER) && pipe.getPluggable(Direction.EAST) == null,
                    "taking a filter off drops it");
            helper.succeed();
        });
    }

    /**
     * A red lens paints items entering through it, which keep the colour when saved and on clients. At a junction, a
     * red filter takes red items and refuses blue ones, a blue filter takes blue ones, and unpainted items go the
     * unfiltered way.
     */
    public static void lensesPaintAndFiltersSort(GameTestHelper helper) {
        chest(helper, new BlockPos(0, 2, 2));
        PipeBlockEntity painter = pipe(helper, new BlockPos(1, 2, 2), BCTransport.STONE_ITEM);
        PipeBlockEntity junction = pipe(helper, new BlockPos(2, 2, 2), BCTransport.STONE_ITEM);
        pipe(helper, new BlockPos(2, 2, 1), BCTransport.STONE_ITEM);
        pipe(helper, new BlockPos(2, 2, 3), BCTransport.STONE_ITEM);
        pipe(helper, new BlockPos(3, 2, 2), BCTransport.STONE_ITEM);
        ChestBlockEntity north = chest(helper, new BlockPos(2, 2, 0));
        ChestBlockEntity south = chest(helper, new BlockPos(2, 2, 4));
        ChestBlockEntity east = chest(helper, new BlockPos(4, 2, 2));
        lens(painter, Direction.WEST, RED_LENS);
        lens(junction, Direction.SOUTH, RED_FILTER);
        lens(junction, Direction.EAST, BLUE_FILTER);

        helper.runAfterDelay(2, () -> {
            ItemFlow junctionItems = items(junction);
            require(helper, !junctionItems.injectItem(new ItemStack(Items.STONE), false, Direction.SOUTH, DyeColor.BLUE, SPEED).isEmpty()
                    && junctionItems.injectItem(new ItemStack(Items.STONE), false, Direction.SOUTH, DyeColor.RED, SPEED).isEmpty()
                    && junctionItems.injectItem(new ItemStack(Items.STONE), false, Direction.SOUTH, null, SPEED).isEmpty(),
                    "a red filter refuses blue items coming in through it but takes red and unpainted ones");
            require(helper, items(painter).injectItem(new ItemStack(Items.COBBLESTONE, 4), true, Direction.WEST, null, SPEED).isEmpty(),
                    "the painting pipe takes items through its lens");
            List<DyeColor> colours = savedColours(painter);
            require(helper, colours.equals(List.of(DyeColor.RED, DyeColor.RED)), "items entering through a red lens are red when saved and on clients, got " + colours);
            junctionItems.injectItem(new ItemStack(Items.DIRT, 4), true, Direction.WEST, null, SPEED);
            junctionItems.injectItem(new ItemStack(Items.SAND, 4), true, Direction.WEST, DyeColor.BLUE, SPEED);
        });
        helper.succeedWhen(() -> {
            require(helper, count(south, Items.COBBLESTONE) == 4 && count(east, Items.SAND) == 4 && count(north, Items.DIRT) == 4,
                    "red items go to the red filter, blue to the blue filter and unpainted the unfiltered way; north " + count(north, Items.DIRT)
                            + " dirt, south " + count(south, Items.COBBLESTONE) + " cobblestone, east " + count(east, Items.SAND) + " sand");
            require(helper, count(north, Items.COBBLESTONE) + count(east, Items.COBBLESTONE) + count(south, Items.SAND) + count(north, Items.SAND)
                    + count(south, Items.DIRT) + count(east, Items.DIRT) == 0, "no item goes another way");
        });
    }

    /**
     * Upstream {@code FilterEventHandler}: a red filter on the next pipe's facing side draws red items towards it and
     * sends unpainted ones elsewhere. A clear lens on the way washes red items, which then go elsewhere.
     */
    public static void neighbourFilterSteersAndClearLensWashes(GameTestHelper helper) {
        chest(helper, new BlockPos(0, 2, 2));
        PipeBlockEntity washer = pipe(helper, new BlockPos(1, 2, 2), BCTransport.STONE_ITEM);
        PipeBlockEntity junction = pipe(helper, new BlockPos(2, 2, 2), BCTransport.STONE_ITEM);
        pipe(helper, new BlockPos(2, 2, 1), BCTransport.STONE_ITEM);
        PipeBlockEntity filtered = pipe(helper, new BlockPos(3, 2, 2), BCTransport.STONE_ITEM);
        ChestBlockEntity north = chest(helper, new BlockPos(2, 2, 0));
        ChestBlockEntity east = chest(helper, new BlockPos(4, 2, 2));
        lens(washer, Direction.EAST, CLEAR_LENS);
        lens(filtered, Direction.WEST, RED_FILTER);

        helper.runAfterDelay(2, () -> {
            items(junction).injectItem(new ItemStack(Items.COBBLESTONE, 4), true, Direction.WEST, DyeColor.RED, SPEED);
            items(junction).injectItem(new ItemStack(Items.DIRT, 4), true, Direction.WEST, null, SPEED);
            items(washer).injectItem(new ItemStack(Items.SAND, 4), true, Direction.WEST, DyeColor.RED, SPEED);
        });
        helper.succeedWhen(() -> {
            require(helper, count(east, Items.COBBLESTONE) == 4 && count(north, Items.DIRT) == 4 && count(north, Items.SAND) == 4,
                    "red items go towards the next pipe's red filter, unpainted and washed ones elsewhere; east " + count(east, Items.COBBLESTONE)
                            + " cobblestone, north " + count(north, Items.DIRT) + " dirt and " + count(north, Items.SAND) + " sand");
            require(helper, count(north, Items.COBBLESTONE) + count(east, Items.DIRT) + count(east, Items.SAND) == 0, "no item goes the other way");
        });
    }
}
