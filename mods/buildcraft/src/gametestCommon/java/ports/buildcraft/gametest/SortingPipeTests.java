package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.chest;
import static ports.buildcraft.gametest.BCTests.count;
import static ports.buildcraft.gametest.BCTests.pipe;
import static ports.buildcraft.gametest.BCTests.require;
import static ports.buildcraft.gametest.BCTests.useAt;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.core.BCCore;
import ports.buildcraft.lib.Power;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.ColouredPipeBlock;
import ports.buildcraft.transport.PipeBlock;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.PipeSide;
import ports.buildcraft.transport.SideCheck;
import ports.buildcraft.transport.behaviour.DaizuliBehaviour;
import ports.buildcraft.transport.behaviour.LapisBehaviour;
import ports.buildcraft.transport.behaviour.ObsidianBehaviour;
import ports.buildcraft.transport.flow.ItemFlow;
import ports.buildcraft.transport.statements.BCTransportStatements;

/** Loader-neutral GameTests of the lapis, daizuli and obsidian pipes. */
public final class SortingPipeTests {
    private static final double SPEED = 0.25;
    private static final StatementParameter[] NO_PARAMETERS = new StatementParameter[0];

    private SortingPipeTests() {}

    /** A real server player holding a wrench; it has no connection and never ticks. */
    private static ServerPlayer wrencher(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "wrencher"), null);
    }

    private static ItemStack wrench() {
        return new ItemStack(BCCore.WRENCH.get());
    }

    private static DyeColor blockColour(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(ColouredPipeBlock.COLOUR);
    }

    /** The items travelling in {@code pipe} with their colours, as the pipe saves them. */
    private static Map<Item, DyeColor> travelling(PipeBlockEntity pipe) {
        CompoundTag saved = new CompoundTag();
        pipe.flow().save(saved);
        Map<Item, DyeColor> colours = new HashMap<>();
        for (Tag entry : saved.getList("items", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) entry;
            int stored = item.getByte("colour");
            colours.put(ItemStack.of(item.getCompound("stack")).getItem(), stored <= 0 ? null : DyeColor.byId(stored - 1));
        }
        return colours;
    }

    private static ItemEntity drop(GameTestHelper helper, Vec3 relative, ItemStack stack) {
        Vec3 at = helper.absoluteVec(relative);
        ItemEntity entity = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack, 0, 0, 0);
        entity.setNoGravity(true);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    /**
     * A wrench on a lapis pipe steps its colour forward, and back while sneaking (white to orange and back, then round
     * to black); the gate paint actions, all sixteen offered, set it. The block shows the colour and saving keeps it.
     * Every item reaching the centre, painted or not, leaves in the pipe's colour.
     */
    public static void lapisPipePaintsItemsAndWrenchCyclesColour(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 2);
        chest(helper, new BlockPos(0, 2, 2));
        PipeBlockEntity lapis = pipe(helper, at, BCTransport.LAPIS_ITEM);
        PipeBlockEntity next = pipe(helper, new BlockPos(2, 2, 2), BCTransport.STONE_ITEM);
        chest(helper, new BlockPos(3, 2, 2));
        LapisBehaviour behaviour = (LapisBehaviour) lapis.behaviour();
        Map<Item, Set<DyeColor>> seen = new HashMap<>();

        helper.runAfterDelay(2, () -> {
            require(helper, behaviour.colour() == DyeColor.WHITE && blockColour(helper, at) == DyeColor.WHITE, "a new lapis pipe is white");
            ServerPlayer player = wrencher(helper);
            require(helper, useAt(helper, player, at, Direction.UP, 0, wrench()).consumesAction() && behaviour.colour() == DyeColor.ORANGE,
                    "a wrench steps the colour from white to orange, got " + behaviour.colour());
            player.setShiftKeyDown(true);
            useAt(helper, player, at, Direction.UP, 0, wrench());
            require(helper, behaviour.colour() == DyeColor.WHITE, "sneaking steps the colour back to white, got " + behaviour.colour());
            useAt(helper, player, at, Direction.UP, 0, wrench());
            require(helper, behaviour.colour() == DyeColor.BLACK, "stepping back from white wraps round to black, got " + behaviour.colour());
            require(helper, blockColour(helper, at) == DyeColor.BLACK, "the block shows the pipe's colour");
            player.setShiftKeyDown(false);
            require(helper, !useAt(helper, player, at, Direction.UP, 0, new ItemStack(Items.STICK)).consumesAction()
                    && behaviour.colour() == DyeColor.BLACK, "only a wrench changes the colour");

            List<Action.Internal> actions = new ArrayList<>();
            behaviour.addInternalActions(actions);
            require(helper, actions.containsAll(List.of(BCTransportStatements.ACTION_PIPE_COLOUR)) && actions.size() == 16,
                    "the lapis pipe offers the sixteen paint actions");
            behaviour.onActionActivate(BCTransportStatements.ACTION_PIPE_COLOUR[DyeColor.RED.getId()], NO_PARAMETERS);
            require(helper, behaviour.colour() == DyeColor.RED && blockColour(helper, at) == DyeColor.RED, "the paint action sets the colour");
            CompoundTag saved = new CompoundTag();
            behaviour.save(saved);
            LapisBehaviour loaded = new LapisBehaviour(lapis);
            loaded.load(saved);
            require(helper, loaded.colour() == DyeColor.RED, "saving keeps the colour");

            ItemFlow items = (ItemFlow) lapis.flow();
            items.injectItem(new ItemStack(Items.COBBLESTONE, 4), true, Direction.WEST, null, SPEED);
            items.injectItem(new ItemStack(Items.SAND, 4), true, Direction.WEST, DyeColor.BLUE, SPEED);
        });
        helper.onEachTick(() -> travelling(next).forEach((item, colour) -> seen.computeIfAbsent(item, i -> new HashSet<>()).add(colour)));
        helper.succeedWhen(() -> {
            Set<DyeColor> red = new HashSet<>();
            red.add(DyeColor.RED);
            require(helper, red.equals(seen.get(Items.COBBLESTONE)) && red.equals(seen.get(Items.SAND)),
                    "unpainted and blue items leave the lapis pipe red, saw " + seen);
        });
    }

    /**
     * A daizuli pipe with no face selected gives items of its colour no exit. A wrench on a connected arm selects
     * that face; on the centre it steps the colour, and the paint action sets it. Items of its colour then leave only
     * through the selected face, and every other item anywhere else.
     */
    public static void daizuliPipeRoutesItsColourToItsFace(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 2, 2);
        chest(helper, new BlockPos(1, 2, 2));
        PipeBlockEntity daizuli = pipe(helper, at, BCTransport.DAIZULI_ITEM);
        ChestBlockEntity north = chest(helper, new BlockPos(2, 2, 1));
        ChestBlockEntity south = chest(helper, new BlockPos(2, 2, 3));
        ChestBlockEntity east = chest(helper, new BlockPos(3, 2, 2));
        DaizuliBehaviour behaviour = (DaizuliBehaviour) daizuli.behaviour();

        helper.runAfterDelay(2, () -> {
            SideCheck unset = new SideCheck(ItemStack.EMPTY, DyeColor.WHITE, Direction.WEST);
            behaviour.itemSideCheck(unset);
            require(helper, behaviour.currentDir() == null && unset.order().isEmpty(),
                    "with no face selected, items of the pipe's colour have no exit");

            ServerPlayer player = wrencher(helper);
            require(helper, useAt(helper, player, at, Direction.NORTH, 0.4, wrench()).consumesAction() && behaviour.currentDir() == Direction.NORTH,
                    "a wrench on the north arm selects the north face, got " + behaviour.currentDir());
            require(helper, helper.getBlockState(at).getValue(PipeBlock.SIDES.get(Direction.NORTH)) == PipeSide.SPECIAL,
                    "the selected face shows the colour");
            useAt(helper, player, at, Direction.UP, 0, wrench());
            require(helper, behaviour.colour() == DyeColor.ORANGE && behaviour.currentDir() == Direction.NORTH,
                    "a wrench on the centre steps the colour and keeps the face, got " + behaviour.colour());
            behaviour.onActionActivate(BCTransportStatements.ACTION_PIPE_COLOUR[DyeColor.RED.getId()], NO_PARAMETERS);
            require(helper, behaviour.colour() == DyeColor.RED && blockColour(helper, at) == DyeColor.RED, "the paint action sets the colour");

            SideCheck matching = new SideCheck(ItemStack.EMPTY, DyeColor.RED, Direction.WEST);
            behaviour.itemSideCheck(matching);
            require(helper, matching.order().equals(List.of(EnumSet.of(Direction.NORTH))), "red items may only go north, got " + matching.order());
            SideCheck other = new SideCheck(ItemStack.EMPTY, DyeColor.BLUE, Direction.WEST);
            behaviour.itemSideCheck(other);
            require(helper, !other.isAllowed(Direction.NORTH) && other.isAllowed(Direction.SOUTH) && other.isAllowed(Direction.EAST),
                    "other colours may go anywhere but north");

            CompoundTag saved = new CompoundTag();
            behaviour.save(saved);
            DaizuliBehaviour loaded = new DaizuliBehaviour(daizuli);
            loaded.load(saved);
            require(helper, loaded.colour() == DyeColor.RED && loaded.currentDir() == Direction.NORTH, "saving keeps the colour and face");

            ItemFlow items = (ItemFlow) daizuli.flow();
            items.injectItem(new ItemStack(Items.COBBLESTONE, 4), true, Direction.WEST, DyeColor.RED, SPEED);
            items.injectItem(new ItemStack(Items.SAND, 4), true, Direction.WEST, DyeColor.BLUE, SPEED);
            items.injectItem(new ItemStack(Items.DIRT, 4), true, Direction.WEST, null, SPEED);
        });
        helper.succeedWhen(() -> {
            require(helper, count(north, Items.COBBLESTONE) == 4, "red items go to the selected face, north has " + count(north, Items.COBBLESTONE));
            require(helper, count(south, Items.SAND) + count(east, Items.SAND) == 4 && count(south, Items.DIRT) + count(east, Items.DIRT) == 4,
                    "blue and unpainted items go elsewhere");
            require(helper, count(north, Items.SAND) + count(north, Items.DIRT) + count(south, Items.COBBLESTONE) + count(east, Items.COBBLESTONE) == 0,
                    "no item goes the wrong way");
        });
    }

    /**
     * An obsidian pipe with one connection sucks items in front of its open face: power that finds nothing in range
     * (an item six blocks out) uses 1 MJ and leaves it; an item two blocks out costs 1/2 MJ plus 1/4 MJ a metre each,
     * so 2 MJ takes two of three. An item inside the pipe's block is taken whole for free. What it takes travels on
     * to the chest behind it. Obsidian pipes do not connect to each other.
     */
    public static void obsidianPipeSucksItemsInRange(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 4);
        ChestBlockEntity chest = chest(helper, new BlockPos(0, 2, 4));
        PipeBlockEntity obsidian = pipe(helper, at, BCTransport.OBSIDIAN_ITEM);
        PipeBlockEntity other = pipe(helper, new BlockPos(1, 2, 3), BCTransport.OBSIDIAN_ITEM);
        ObsidianBehaviour behaviour = (ObsidianBehaviour) obsidian.behaviour();
        ItemEntity[] entities = new ItemEntity[2];

        helper.runAfterDelay(2, () -> {
            require(helper, !obsidian.isConnected(Direction.NORTH) && !other.isConnected(Direction.SOUTH), "obsidian pipes do not connect to each other");
            require(helper, behaviour.openFace() == Direction.EAST, "the open face is opposite the chest, got " + behaviour.openFace());

            ItemEntity far = drop(helper, new Vec3(7.5, 2.5, 4.5), new ItemStack(Items.GOLD_INGOT));
            long leftOver = behaviour.receiveRedstonePower(4 * Power.MJ, false);
            require(helper, leftOver == 3 * Power.MJ && far.isAlive() && far.getItem().getCount() == 1,
                    "power finding nothing in range uses 1 MJ and leaves the far item, left " + leftOver);

            ItemEntity near = drop(helper, new Vec3(3.5, 2.5, 4.5), new ItemStack(Items.DIAMOND, 3));
            long perItem = 2 * ObsidianBehaviour.POWER_PER_METRE + ObsidianBehaviour.POWER_PER_ITEM;
            leftOver = behaviour.receiveRedstonePower(2 * perItem, false);
            require(helper, leftOver == 0 && near.isAlive() && near.getItem().getCount() == 1,
                    "2 MJ takes two of three items two blocks out, left " + leftOver + " and " + near.getItem());
            require(helper, far.isAlive() && far.getItem().getCount() == 1, "the far item stays");

            ItemEntity inside = drop(helper, new Vec3(1.5, 2.5, 4.5), new ItemStack(Items.EMERALD, 5));
            helper.getBlockState(at).entityInside(helper.getLevel(), helper.absolutePos(at), inside);
            require(helper, inside.isRemoved(), "an item inside the pipe is taken whole");
            entities[0] = far;
            entities[1] = near;
        });
        helper.succeedWhen(() -> {
            require(helper, count(chest, Items.DIAMOND) == 2 && count(chest, Items.EMERALD) == 5,
                    "what the pipe takes reaches the chest; it has " + count(chest, Items.DIAMOND) + " diamonds and " + count(chest, Items.EMERALD) + " emeralds");
            require(helper, count(chest, Items.GOLD_INGOT) == 0 && entities[0] != null && entities[0].isAlive(), "the far item is never taken");
        });
    }
}
