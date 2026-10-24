package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.chest;
import static ports.buildcraft.gametest.BCTests.count;
import static ports.buildcraft.gametest.BCTests.pipe;
import static ports.buildcraft.gametest.BCTests.require;
import static ports.buildcraft.gametest.BCTests.useAt;
import static ports.buildcraft.gametest.BCTests.useOn;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.core.BCCore;
import ports.buildcraft.core.MapLocationItem;
import ports.buildcraft.core.statements.BCCoreStatements;
import ports.buildcraft.lib.Power;
import ports.buildcraft.robotics.BCRobotics;
import ports.buildcraft.robotics.DockingStationPluggable;
import ports.buildcraft.robotics.PendingUnlinks;
import ports.buildcraft.robotics.RobotEntity;
import ports.buildcraft.robotics.RobotItem;
import ports.buildcraft.robotics.StationRef;
import ports.buildcraft.robotics.WorkArea;
import ports.buildcraft.robotics.board.BreakerBoard;
import ports.buildcraft.robotics.board.RobotBoardType;
import ports.buildcraft.robotics.statements.BCRoboticsStatements;
import ports.buildcraft.robotics.zone.ZonePlan;
import ports.buildcraft.silicon.AssemblyRecipe;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.silicon.gate.EnumGateLogic;
import ports.buildcraft.silicon.gate.EnumGateMaterial;
import ports.buildcraft.silicon.gate.EnumGateModifier;
import ports.buildcraft.silicon.gate.GateLogic;
import ports.buildcraft.silicon.gate.GateVariant;
import ports.buildcraft.silicon.gate.PluggableGate;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.ActionWrapper;
import ports.buildcraft.statement.StatementManager;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.StatementParameterItemStack;
import ports.buildcraft.statement.TriggerWrapper;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlockEntity;

/**
 * Loader-neutral GameTests of the port's clean-room robots: docking stations, robot items and boards, charging, the
 * picker, carrier, lumberjack and miner boards, and the robot gate statements. Every robot that works is given a
 * gate's "Work in Area" over its own arena, so robots in neighbouring tests never wander into each other's work.
 */
public final class RobotTests {
    /** How far from a pipe's middle a click lands on a docking station, which reaches 4 pixels in from its face. */
    private static final double ON_STATION = 0.5 - 2 / 16.0;
    private static final GateVariant PLAIN = new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER);
    private static final GateVariant ONE_PARAMETER = new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.QUARTZ);
    private static final StatementParameter[] NO_PARAMETERS = new StatementParameter[0];

    private RobotTests() {}

    // Helpers.

    private static DockingStationPluggable station(PipeBlockEntity pipe, Direction side) {
        DockingStationPluggable station = (DockingStationPluggable) BCRobotics.DOCKING_STATION.create(pipe, side);
        pipe.replacePluggable(side, station);
        return station;
    }

    /** Uses a charged robot item carrying {@code board} on the station, as a player would, and returns the robot. */
    private static RobotEntity deploy(GameTestHelper helper, Player player, BlockPos pos, DockingStationPluggable station,
            RobotBoardType board) {
        ItemStack item = RobotItem.create(board, RobotEntity.CAPACITY);
        require(helper, useAt(helper, player, pos, station.side, ON_STATION, item) == InteractionResult.CONSUME && item.isEmpty(),
                "using a robot on a free docking station deploys it");
        RobotEntity robot = station.dockedRobot();
        require(helper, robot != null && station.linkedRobot() == robot, "the deployed robot is docked at the station and lives there");
        return robot;
    }

    /** A map location of the area between two corners of the test, in absolute coordinates. */
    private static ItemStack areaMap(GameTestHelper helper, BlockPos from, BlockPos to) {
        ItemStack map = new ItemStack(BCCore.MAP_LOCATION.get());
        MapLocationItem.Type.AREA.set(map);
        BlockPos a = helper.absolutePos(from), b = helper.absolutePos(to);
        CompoundTag tag = map.getOrCreateTag();
        tag.putInt("xMin", Math.min(a.getX(), b.getX()));
        tag.putInt("yMin", Math.min(a.getY(), b.getY()));
        tag.putInt("zMin", Math.min(a.getZ(), b.getZ()));
        tag.putInt("xMax", Math.max(a.getX(), b.getX()));
        tag.putInt("yMax", Math.max(a.getY(), b.getY()));
        tag.putInt("zMax", Math.max(a.getZ(), b.getZ()));
        return map;
    }

    /** A gate on {@code side} that always runs {@code action} (none if null) with {@code parameters}. */
    private static PluggableGate gate(PipeBlockEntity pipe, Direction side, GateVariant variant, @Nullable Action action, ItemStack... parameters) {
        PluggableGate gate = new PluggableGate(BCSilicon.GATE, pipe, side, variant);
        pipe.replacePluggable(side, gate);
        gate.logic().statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        gate.logic().statements[0].action.set(ActionWrapper.wrap(action, null));
        for (int i = 0; i < parameters.length; i++) gate.logic().statements[0].action.set(i, new StatementParameterItemStack(parameters[i]));
        gate.logic().onTick();
        return gate;
    }

    /**
     * Keeps the robots that live on {@code pipe} inside the test's arena with a "Work in Area" gate, configured by
     * {@code owner} as robots only take an area from a gate their owner configured.
     */
    private static void confine(GameTestHelper helper, PipeBlockEntity pipe, Direction side, Player owner) {
        PluggableGate gate = gate(pipe, side, ONE_PARAMETER, BCRoboticsStatements.ACTION_WORK_IN_AREA,
                areaMap(helper, new BlockPos(0, 1, 0), new BlockPos(8, 6, 8)));
        gate.logic().setEditor(GateLogic.Editor.of(owner));
    }

    private static void done(GameTestHelper helper, RobotEntity... robots) {
        for (RobotEntity robot : robots) robot.discard();
        helper.succeed();
    }

    /** A position relative to the test; 1.19.2's {@code GameTestHelper.relativePos} mirrors x and z. */
    private static BlockPos relative(GameTestHelper helper, BlockPos pos) {
        return pos.subtract(helper.absolutePos(BlockPos.ZERO));
    }

    /** The robot's state, for failure messages. */
    private static String describe(GameTestHelper helper, RobotEntity robot) {
        StringBuilder text = new StringBuilder("robot at ").append(relative(helper, robot.blockPosition()))
                .append(robot.dockedAt() != null ? " docked" : "").append(robot.isSleeping() ? " asleep" : "")
                .append(robot.isRecharging() ? " recharging" : "").append(" energy ").append(Power.formatMj(robot.battery().stored()))
                .append(" tool ").append(robot.tool()).append(" carrying");
        for (int slot = 0; slot < robot.inventory().getContainerSize(); slot++) text.append(' ').append(robot.inventory().getItem(slot));
        List<net.minecraft.world.entity.item.ItemEntity> items = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0, -3, 0)), helper.absolutePos(new BlockPos(9, 8, 9))));
        text.append("; items lying:");
        for (var item : items) text.append(' ').append(item.getItem()).append('@').append(relative(helper, item.blockPosition()));
        return text.toString();
    }

    private static CraftingContainer grid() {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override public boolean stillValid(Player player) { return true; }
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        };
        return new CraftingContainer(menu, 3, 3);
    }

    // Tests.

    /**
     * The robot, redstone board and docking station have survival crafting recipes, each programmed board is a
     * redstone board and a tool assembled for 10,000 MJ, and a board crafts into a robot (the old board comes back)
     * or out of one. A robot item keeps its board and energy.
     */
    public static void robotRecipesAndItems(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        for (String id : List.of("robot", "redstone_board", "docking_station", "robot_board")) {
            require(helper, recipes.byKey(BuildCraft.id(id)).isPresent(), "recipe " + id + " exists");
        }
        for (RobotBoardType type : RobotBoardType.values()) {
            Recipe<?> recipe = recipes.byKey(BuildCraft.id("assembly/board_" + type.id)).orElse(null);
            require(helper, recipe instanceof AssemblyRecipe assembly && assembly.getResultItem().is(BCRobotics.BOARDS.get(type).get())
                    && assembly.getRequiredMicroJoulesFor(ItemStack.EMPTY) == 10_000 * Power.MJ
                    && assembly.getInputsFor(ItemStack.EMPTY).get(0).ingredient().test(new ItemStack(BCRobotics.REDSTONE_BOARD.get())),
                    "the " + type.id + " board is a redstone board assembled with a tool for 10,000 MJ");
        }

        CraftingRecipe boardRecipe = (CraftingRecipe) recipes.byKey(BuildCraft.id("robot_board")).orElseThrow();
        CraftingContainer install = grid();
        install.setItem(0, RobotItem.create(RobotBoardType.PICKER, 5 * Power.MJ));
        install.setItem(4, new ItemStack(BCRobotics.BOARDS.get(RobotBoardType.MINER).get()));
        require(helper, boardRecipe.matches(install, helper.getLevel()), "a robot and a board craft together");
        ItemStack programmed = boardRecipe.assemble(install);
        require(helper, programmed.is(BCRobotics.ROBOT_ITEM.get()) && RobotItem.board(programmed) == RobotBoardType.MINER
                && RobotItem.energy(programmed) == 5 * Power.MJ, "the crafted robot carries the new board and keeps its energy");
        NonNullList<ItemStack> left = boardRecipe.getRemainingItems(install);
        require(helper, left.get(0).is(BCRobotics.BOARDS.get(RobotBoardType.PICKER).get()), "the robot's old board comes back");

        CraftingContainer remove = grid();
        remove.setItem(3, RobotItem.create(RobotBoardType.CARRIER, 0));
        require(helper, boardRecipe.matches(remove, helper.getLevel()) && RobotItem.board(boardRecipe.assemble(remove)) == null
                && boardRecipe.getRemainingItems(remove).get(3).is(BCRobotics.BOARDS.get(RobotBoardType.CARRIER).get()),
                "a programmed robot alone crafts into a robot without a board, giving the board back");
        CraftingContainer blank = grid();
        blank.setItem(0, RobotItem.create(null, 0));
        blank.setItem(1, new ItemStack(BCRobotics.REDSTONE_BOARD.get()));
        require(helper, !boardRecipe.matches(blank, helper.getLevel()), "a clean redstone board is not a programmed board");
        helper.succeed();
    }

    /**
     * A robot that stops calling a station home while that station's chunk is unloaded (here, picked up after moving
     * its home to a station in a far unloaded chunk) leaves a pending unlink, saved with the level. A station named in
     * one drops the link on its next tick, so a new robot can be deployed there instead of the station staying
     * reserved forever.
     */
    public static void stationForgetsRobotsTakenWhileUnloaded(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 4);
        PipeBlockEntity pipe = pipe(helper, pos, BCTransport.STONE_ITEM);
        DockingStationPluggable station = station(pipe, Direction.UP);
        Player player = helper.makeMockPlayer();
        PendingUnlinks pending = PendingUnlinks.get(helper.getLevel());

        RobotEntity robot = deploy(helper, player, pos, station, RobotBoardType.PICKER);
        StationRef far = new StationRef(helper.absolutePos(pos).offset(100_000, 0, 100_000), Direction.UP);
        require(helper, !helper.getLevel().isLoaded(far.pos()), "the far station's chunk is unloaded");
        robot.setHome(far);
        require(helper, !station.isLinked() && far.equals(robot.home()), "moving the robot's home frees its loaded old station");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        require(helper, robot.interact(player, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME && robot.isRemoved(),
                "the owner picks the robot up");
        require(helper, robot.getUUID().equals(pending.peek(far)), "picking up a robot whose home is unloaded leaves a pending unlink");
        player.setShiftKeyDown(false);

        RobotEntity away = deploy(helper, player, pos, station, RobotBoardType.PICKER);
        away.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        require(helper, station.isLinked(), "the station stays linked to its unloaded robot");
        pending.add(station.ref(), away.getUUID());
        PendingUnlinks saved = PendingUnlinks.load(pending.save(new CompoundTag()));
        require(helper, away.getUUID().equals(saved.peek(station.ref())) && robot.getUUID().equals(saved.peek(far)),
                "pending unlinks are saved with the level");

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    require(helper, !station.isLinked() && pending.peek(station.ref()) == null,
                            "the station drops the link its robot gave up and forgets the pending unlink");
                    deploy(helper, player, pos, station, RobotBoardType.PICKER).discard();
                })
                .thenSucceed();
    }

    /**
     * A docking station goes on any pipe face, blocks it and picks as its own item. Using a robot on it deploys the
     * robot docked there and calling it home; the station saves which robot lives there and tells clients whether a
     * robot is linked and docked. A robot saves its board, energy and home. Sneak-using a robot with an empty hand
     * takes it back as an item with its board and energy, freeing the station; using a board on a robot swaps its
     * board, giving the old one back.
     */
    public static void stationDeploysAndReturnsRobots(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 4);
        PipeBlockEntity pipe = pipe(helper, pos, BCTransport.STONE_ITEM);
        Player player = helper.makeMockPlayer();
        ItemStack stations = new ItemStack(BCRobotics.DOCKING_STATION_ITEM.get(), 2);
        require(helper, useOn(helper, player, pos, Direction.UP, stations) == InteractionResult.CONSUME && stations.getCount() == 1,
                "a docking station placed on a pipe face uses one item");
        require(helper, pipe.getPluggable(Direction.UP) instanceof DockingStationPluggable && pipe.isBlocked(Direction.UP)
                && pipe.getPluggable(Direction.UP).getPickStack().is(BCRobotics.DOCKING_STATION_ITEM.get()),
                "the station blocks its face and picks as its own item");
        DockingStationPluggable station = (DockingStationPluggable) pipe.getPluggable(Direction.UP);

        RobotEntity robot = deploy(helper, player, pos, station, RobotBoardType.PICKER);
        require(helper, robot.boardType() == RobotBoardType.PICKER && robot.battery().stored() == RobotEntity.CAPACITY,
                "the robot keeps its item's board and energy");
        require(helper, robot.owner().getName().equals(player.getGameProfile().getName()), "the robot works for the player who deployed it");
        require(helper, useAt(helper, player, pos, Direction.UP, ON_STATION, RobotItem.create(null, 0)) == InteractionResult.FAIL,
                "a second robot cannot be deployed on an occupied station");

        PipeBlockEntity loaded = new PipeBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        loaded.load(pipe.saveWithoutMetadata());
        require(helper, loaded.getPluggable(Direction.UP) instanceof DockingStationPluggable, "saving keeps the station");
        CompoundTag data = pipe.getUpdateTag().getCompound("plugs").getCompound("up").getCompound("data");
        require(helper, data.getBoolean("linked") && data.getBoolean("docked"), "clients hear the station is linked and docked, got " + data);

        CompoundTag saved = robot.saveWithoutId(new CompoundTag());
        RobotEntity copy = new RobotEntity(BCRobotics.ROBOT_TYPE.get(), helper.getLevel());
        copy.load(saved);
        require(helper, copy.boardType() == RobotBoardType.PICKER && copy.battery().stored() == RobotEntity.CAPACITY
                && station.ref().equals(copy.home()) && copy.owner().getName().equals(player.getGameProfile().getName()),
                "saving a robot keeps its board, energy, home and owner");

        ItemStack miner = new ItemStack(BCRobotics.BOARDS.get(RobotBoardType.MINER).get());
        Player stranger = helper.makeMockPlayer();
        stranger.setItemInHand(InteractionHand.MAIN_HAND, miner.copy());
        require(helper, robot.interact(stranger, InteractionHand.MAIN_HAND) == InteractionResult.FAIL
                && robot.boardType() == RobotBoardType.PICKER && stranger.getMainHandItem().getCount() == 1,
                "another player cannot reprogram someone else's robot");
        stranger.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        stranger.setShiftKeyDown(true);
        require(helper, robot.interact(stranger, InteractionHand.MAIN_HAND) == InteractionResult.FAIL && !robot.isRemoved(),
                "another player cannot pick up someone else's robot");
        player.setItemInHand(InteractionHand.MAIN_HAND, miner);
        require(helper, robot.interact(player, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME && miner.isEmpty()
                && robot.boardType() == RobotBoardType.MINER, "using a board on a robot installs it");
        require(helper, player.getInventory().countItem(BCRobotics.BOARDS.get(RobotBoardType.PICKER).get()) == 1,
                "the robot's old board comes back to the player");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        require(helper, robot.interact(player, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME && robot.isRemoved(),
                "sneak-using a robot with an empty hand picks it up");
        int robots = player.getInventory().countItem(BCRobotics.ROBOT_ITEM.get());
        ItemStack returned = ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(BCRobotics.ROBOT_ITEM.get())) returned = player.getInventory().getItem(slot);
        }
        require(helper, robots == 1 && RobotItem.board(returned) == RobotBoardType.MINER && RobotItem.energy(returned) > 0,
                "the robot comes back as an item with its board and energy");
        require(helper, !station.isLinked() && !station.hasDockedRobot(), "taking the robot frees its station");

        RobotEntity away = deploy(helper, player, pos, station, RobotBoardType.PICKER);
        away.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        require(helper, useAt(helper, player, pos, Direction.UP, ON_STATION, RobotItem.create(null, 0)) == InteractionResult.FAIL,
                "a station stays reserved for its robot while the robot is in unloaded chunks");

        ItemStack badZone = new ItemStack(BCCore.MAP_LOCATION.get());
        MapLocationItem.Type.ZONE.set(badZone);
        ListTag notChunks = new ListTag();
        notChunks.add(IntTag.valueOf(7));
        badZone.getOrCreateTag().put("chunkMapping", notChunks);
        require(helper, WorkArea.fromMap(badZone, helper.absolutePos(pos)) == null, "a malformed zone map gives no work area");

        BlockPos origin = helper.absolutePos(pos);
        ZonePlan small = new ZonePlan();
        small.paintRectangle(origin.getX(), origin.getZ(), origin.getX() + 3, origin.getZ() + 3, true);
        ItemStack smallZone = new ItemStack(BCCore.MAP_LOCATION.get());
        MapLocationItem.setZone(smallZone, small);
        WorkArea zoned = WorkArea.fromMap(smallZone, origin);
        require(helper, zoned != null && zoned.contains(origin) && !zoned.contains(origin.offset(5, 0, 5)),
                "a painted zone gives its columns as the work area, got " + zoned);
        ZonePlan huge = new ZonePlan();
        for (int chunk = 0; chunk <= WorkArea.MAX_ZONE_CHUNKS; chunk++) huge.set(chunk * 16, 0, true);
        ItemStack hugeZone = new ItemStack(BCCore.MAP_LOCATION.get());
        MapLocationItem.setZone(hugeZone, huge);
        require(helper, WorkArea.fromMap(hugeZone, origin) == null, "a zone over " + WorkArea.MAX_ZONE_CHUNKS + " chunks is refused");
        ItemStack fatZone = smallZone.copy();
        fatZone.getTag().getList("chunkMapping", Tag.TAG_COMPOUND).getCompound(0).putByteArray("bits", new byte[4096]);
        require(helper, WorkArea.fromMap(fatZone, origin) == null, "a zone chunk with more bits than columns is refused");

        ItemStack farMap = new ItemStack(BCCore.MAP_LOCATION.get());
        MapLocationItem.Type.AREA.set(farMap);
        for (String key : List.of("xMin", "yMin", "zMin", "xMax", "yMax", "zMax")) farMap.getOrCreateTag().putInt(key, Integer.MAX_VALUE);
        WorkArea far = WorkArea.fromMap(farMap, helper.absolutePos(pos));
        require(helper, far != null && far.bounds().getXsize() <= WorkArea.MAX_SIZE && far.bounds().getYsize() <= WorkArea.MAX_SIZE
                && far.bounds().getZsize() <= WorkArea.MAX_SIZE, "an area at the integer limit stays a small box, got " + (far == null ? null : far.bounds()));
        helper.succeed();
    }

    /**
     * A station on a kinesis pipe charges the robot docked at it from the pipe's power. A robot running low leaves
     * its work, flies home to its charging station and stays until nearly full, then goes back to work.
     */
    public static void robotRechargesAtItsStation(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 4);
        PipeBlockEntity pipe = pipe(helper, pos, BCTransport.WOOD_POWER);
        DockingStationPluggable station = station(pipe, Direction.UP);
        Player owner = helper.makeMockPlayer();
        confine(helper, pipe, Direction.NORTH, owner);
        require(helper, station.canCharge(), "a station on a kinesis pipe charges robots");
        RobotEntity robot = deploy(helper, owner, pos, station, RobotBoardType.PICKER);
        robot.undock();
        robot.setPos(robot.getX() + 3, robot.getY(), robot.getZ() + 2);
        robot.battery().set(RobotEntity.LOW_ENERGY - Power.MJ);
        helper.onEachTick(() -> pipe.energyPort(Direction.EAST).receive(Power.toUnits(10 * Power.MJ), false));
        long[] before = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> require(helper, robot.isRecharging() && station.dockedRobot() == robot,
                        "a robot low on energy flies home to its charging station and docks"))
                .thenExecute(() -> before[0] = robot.battery().stored())
                .thenIdle(20)
                .thenExecute(() -> require(helper, robot.battery().stored() > before[0],
                        "the docked robot charges from the kinesis pipe, " + before[0] + " -> " + robot.battery().stored()))
                .thenExecute(() -> robot.battery().set(RobotEntity.RESUME_ENERGY))
                .thenIdle(2)
                .thenExecute(() -> require(helper, !robot.isRecharging(), "a nearly full robot goes back to work"))
                .thenExecute(() -> done(helper, robot))
                .thenSucceed();
    }

    /** The picker collects the items lying in its area and brings them home, where the station's pipe takes them to a chest. */
    public static void pickerCollectsItemsToItsStation(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 4);
        ChestBlockEntity chest = chest(helper, new BlockPos(4, 1, 4));
        PipeBlockEntity pipe = pipe(helper, pos, BCTransport.STONE_ITEM);
        DockingStationPluggable station = station(pipe, Direction.UP);
        Player owner = helper.makeMockPlayer();
        confine(helper, pipe, Direction.NORTH, owner);
        // The arena floor is at y = 1: the items rest on it (an item spawned inside the floor may be pushed out below
        // it, out of the work area). Neighbouring tests' drops may land here too, so count only the named items.
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(6, 1, 6), Blocks.STONE);
        Component name = Component.literal("picker test");
        ItemStack cobblestone = helper.spawnItem(Items.COBBLESTONE, 2.5F, 2.1F, 2.5F).getItem();
        cobblestone.setCount(8);
        cobblestone.setHoverName(name);
        helper.spawnItem(Items.DIAMOND, 6.5F, 2.1F, 6.5F).getItem().setHoverName(name);
        RobotEntity robot = deploy(helper, owner, pos, station, RobotBoardType.PICKER);
        helper.succeedWhen(() -> {
            require(helper, countNamed(chest, Items.COBBLESTONE, name) == 8 && countNamed(chest, Items.DIAMOND, name) == 1,
                    "the picker brings the cobblestone and the diamond home, chest has " + countNamed(chest, Items.COBBLESTONE, name)
                            + " and " + countNamed(chest, Items.DIAMOND, name) + "; " + describe(helper, robot));
            robot.discard();
        });
    }

    private static int countNamed(ChestBlockEntity chest, Item item, Component name) {
        int total = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.is(item) && stack.hasCustomHoverName() && stack.getHoverName().getString().equals(name.getString())) total += stack.getCount();
        }
        return total;
    }

    /**
     * The carrier loads at a station whose gate provides items, from the chest beside its pipe, and delivers to a
     * station whose gate accepts items, whose pipe takes them to its chest.
     */
    public static void carrierMovesItemsBetweenStations(GameTestHelper helper) {
        BlockPos homePos = new BlockPos(4, 2, 4);
        PipeBlockEntity home = pipe(helper, homePos, BCTransport.STONE_ITEM);
        DockingStationPluggable homeStation = station(home, Direction.UP);
        Player owner = helper.makeMockPlayer();
        confine(helper, home, Direction.NORTH, owner);

        ChestBlockEntity source = chest(helper, new BlockPos(1, 1, 1), new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.DIRT, 4));
        PipeBlockEntity provider = pipe(helper, new BlockPos(1, 2, 1), BCTransport.STONE_ITEM);
        station(provider, Direction.UP);
        gate(provider, Direction.NORTH, ONE_PARAMETER, BCRoboticsStatements.ACTION_PROVIDE_ITEMS, new ItemStack(Items.COBBLESTONE));

        ChestBlockEntity target = chest(helper, new BlockPos(7, 1, 7));
        PipeBlockEntity acceptor = pipe(helper, new BlockPos(7, 2, 7), BCTransport.STONE_ITEM);
        station(acceptor, Direction.UP);
        gate(acceptor, Direction.NORTH, PLAIN, BCRoboticsStatements.ACTION_ACCEPT_ITEMS);

        RobotEntity robot = deploy(helper, owner, homePos, homeStation, RobotBoardType.CARRIER);
        helper.succeedWhen(() -> {
            require(helper, count(target, Items.COBBLESTONE) == 16, "the carrier moves the cobblestone, target has " + count(target, Items.COBBLESTONE)
                    + "; " + describe(helper, robot));
            require(helper, count(source, Items.DIRT) == 4 && count(target, Items.DIRT) == 0, "the provider's filter keeps the dirt");
            robot.discard();
        });
    }

    /** The lumberjack takes an axe from the chest beside its station, cuts the logs in its area and brings them home. */
    public static void lumberjackCutsLogsWithAnAxeFromHome(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 4);
        ChestBlockEntity chest = chest(helper, new BlockPos(4, 1, 4), new ItemStack(Items.IRON_AXE));
        PipeBlockEntity pipe = pipe(helper, pos, BCTransport.STONE_ITEM);
        DockingStationPluggable station = station(pipe, Direction.UP);
        Player owner = helper.makeMockPlayer();
        confine(helper, pipe, Direction.NORTH, owner);
        BlockPos[] logs = {new BlockPos(1, 1, 1), new BlockPos(1, 2, 1), new BlockPos(7, 1, 2)};
        for (BlockPos log : logs) helper.setBlock(log, Blocks.OAK_LOG);
        require(helper, BreakerBoard.isAxe(new ItemStack(Items.IRON_AXE)) && !BreakerBoard.isAxe(new ItemStack(Items.IRON_PICKAXE)),
                "the lumberjack takes axes, not pickaxes");
        RobotEntity robot = deploy(helper, owner, pos, station, RobotBoardType.LUMBERJACK);
        helper.succeedWhen(() -> {
            for (BlockPos log : logs) require(helper, helper.getBlockState(log).isAir(), "the lumberjack cuts the log at " + log);
            require(helper, count(chest, Items.OAK_LOG) == 3, "the lumberjack brings the logs home, chest has " + count(chest, Items.OAK_LOG));
            require(helper, count(chest, Items.IRON_AXE) == 0 && robot.tool().is(Items.IRON_AXE), "the lumberjack works with the chest's axe");
            robot.discard();
        });
    }

    /**
     * The miner digs ores open to air with a pickaxe from home and brings them back. It leaves ores sealed in stone,
     * and ores protection refuses its owner, whom the loader's break event names.
     */
    public static void minerDigsOpenOresItMayBreak(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 4);
        ChestBlockEntity chest = chest(helper, new BlockPos(4, 1, 4), new ItemStack(Items.IRON_PICKAXE));
        PipeBlockEntity pipe = pipe(helper, pos, BCTransport.STONE_ITEM);
        DockingStationPluggable station = station(pipe, Direction.UP);
        Player player = helper.makeMockPlayer();
        confine(helper, pipe, Direction.NORTH, player);
        BlockPos open = new BlockPos(1, 1, 1);
        BlockPos refused = new BlockPos(7, 1, 1);
        BlockPos sealed = new BlockPos(6, 2, 6);
        helper.setBlock(open, Blocks.COAL_ORE);
        helper.setBlock(refused, Blocks.COAL_ORE);
        helper.setBlock(sealed, Blocks.IRON_ORE);
        for (Direction side : Direction.values()) helper.setBlock(sealed.relative(side), Blocks.STONE);
        TestProtection.refuse(helper, refused);
        require(helper, BreakerBoard.isOre(Blocks.COAL_ORE.defaultBlockState()) && BreakerBoard.isOre(Blocks.DEEPSLATE_IRON_ORE.defaultBlockState())
                && !BreakerBoard.isOre(Blocks.STONE.defaultBlockState()), "the miner knows ores from stone");
        RobotEntity robot = deploy(helper, player, pos, station, RobotBoardType.MINER);
        // Protection is asked while the miner looks for ores, so it never sets off for the refused one.
        Vec3 refusedCentre = helper.absoluteVec(Vec3.atCenterOf(refused));
        double[] closest = {Double.MAX_VALUE};
        helper.onEachTick(() -> closest[0] = Math.min(closest[0], robot.position().distanceTo(refusedCentre)));
        helper.succeedWhen(() -> {
            require(helper, closest[0] > 2, "the miner never flies to the protected ore, came within " + closest[0]);
            require(helper, helper.getBlockState(open).isAir(), "the miner digs the open ore");
            require(helper, count(chest, Items.COAL) >= 1, "the miner brings the coal home");
            require(helper, player.getGameProfile().getName().equals(TestProtection.refusedPlayer(helper, refused)),
                    "the break event names the robot's owner, was " + TestProtection.refusedPlayer(helper, refused));
            require(helper, helper.getBlockState(refused).is(Blocks.COAL_ORE), "the miner leaves the protected ore");
            require(helper, helper.getBlockState(sealed).is(Blocks.IRON_ORE), "the miner leaves the ore sealed in stone, found "
                    + helper.getBlockState(sealed) + "; " + describe(helper, robot));
            TestProtection.allow(helper, refused);
            robot.discard();
        });
    }

    /**
     * Gates on a pipe with a docking station are offered the robot triggers and station actions, and gates on other
     * pipes are not. The triggers read whether a robot is docked, linked, reserved or asleep; Wake Up wakes the
     * robot, Forbid Robot keeps other robots away while held, Drop Items In Pipe and Work in Area apply while held.
     */
    public static void robotStatements(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        PipeBlockEntity pipe = pipe(helper, pos, BCTransport.STONE_ITEM);
        PipeBlockEntity bare = pipe(helper, new BlockPos(6, 2, 6), BCTransport.STONE_ITEM);
        PluggableGate bareGate = gate(bare, Direction.NORTH, PLAIN, null);
        require(helper, bareGate.logic().getAllValidTriggers().stream().noneMatch(trigger -> List.of(BCRoboticsStatements.TRIGGERS).contains(trigger.delegate)),
                "a pipe without a station offers no robot triggers");
        DockingStationPluggable station = station(pipe, Direction.UP);
        PluggableGate gate = gate(pipe, Direction.NORTH, ONE_PARAMETER, null);
        GateLogic logic = gate.logic();
        for (var trigger : BCRoboticsStatements.TRIGGERS) {
            require(helper, logic.getAllValidTriggers().stream().filter(wrapper -> wrapper.delegate == trigger).count() == 1,
                    "a pipe with a station offers " + trigger.getUniqueTag() + " once");
        }
        for (var action : BCRoboticsStatements.ACTIONS) {
            require(helper, logic.getAllValidActions().stream().filter(wrapper -> wrapper.delegate == action).count() == 1,
                    "a pipe with a station offers " + action.getUniqueTag() + " once");
            require(helper, StatementManager.getStatement(action.getUniqueTag()) == action, action.getUniqueTag() + " is registered");
        }
        require(helper, !BCRoboticsStatements.TRIGGER_ROBOT_IN_STATION.isTriggerActive(logic, NO_PARAMETERS)
                && !BCRoboticsStatements.TRIGGER_STATION_LINKED.isTriggerActive(logic, NO_PARAMETERS),
                "an empty station has no robot docked or linked");

        Player owner = helper.makeMockPlayer();
        RobotEntity robot = deploy(helper, owner, pos, station, RobotBoardType.PICKER);
        robot.setBoard(null);
        require(helper, BCRoboticsStatements.TRIGGER_ROBOT_IN_STATION.isTriggerActive(logic, NO_PARAMETERS)
                && BCRoboticsStatements.TRIGGER_STATION_LINKED.isTriggerActive(logic, NO_PARAMETERS)
                && !BCRoboticsStatements.TRIGGER_STATION_RESERVED.isTriggerActive(logic, NO_PARAMETERS),
                "a deployed robot is docked and linked, and nobody is on the way");
        RobotEntity visitor = new RobotEntity(BCRobotics.ROBOT_TYPE.get(), helper.getLevel());
        visitor.battery().set(RobotEntity.CAPACITY);
        visitor.setPos(helper.absoluteVec(new Vec3(6.5, 4, 6.5)));
        helper.getLevel().addFreshEntity(visitor);

        helper.startSequence()
                .thenWaitUntil(() -> require(helper, robot.isSleeping() && BCRoboticsStatements.TRIGGER_ROBOT_SLEEP.isTriggerActive(logic, NO_PARAMETERS),
                        "a robot with nothing to do falls asleep at home, and the gate sees it"))
                .thenExecute(() -> {
                    BCRoboticsStatements.ACTION_WAKE_UP.actionActivate(logic, NO_PARAMETERS);
                    require(helper, !robot.isSleeping(), "Wake Up wakes the robot that lives here");
                    require(helper, station.isFreeFor(robot) && !station.isFreeFor(visitor), "another robot may not dock while one is docked");
                    robot.undock();
                    require(helper, station.isFreeFor(visitor), "an empty station is free for other robots");
                    BCRoboticsStatements.ACTION_FORBID_ROBOT.actionActivate(logic, NO_PARAMETERS);
                    require(helper, !station.isFreeFor(visitor) && station.isFreeFor(robot), "Forbid Robot keeps away all but the robot that lives here");
                    BCRoboticsStatements.ACTION_DROP_IN_PIPE.actionActivate(logic, NO_PARAMETERS);
                    require(helper, station.isDroppingInPipe() && station.accepts(visitor, new ItemStack(Items.DIRT)),
                            "Drop Items In Pipe takes anything into an item pipe");
                    ItemStack map = areaMap(helper, new BlockPos(1, 1, 1), new BlockPos(3, 3, 3));
                    StatementParameter[] mapParameter = {new StatementParameterItemStack(map)};
                    BlockPos outside = helper.absolutePos(new BlockPos(5, 1, 1));
                    // The robot breaks blocks as its owner, so only a gate its owner (or an operator) configured may move it.
                    logic.setEditor(null);
                    BCRoboticsStatements.ACTION_WORK_IN_AREA.actionActivate(logic, mapParameter);
                    require(helper, station.gateArea(robot) == null && robot.workArea().contains(outside),
                            "a gate nobody configured gives someone's robot no area");
                    logic.setEditor(new GateLogic.Editor(UUID.randomUUID(), false));
                    BCRoboticsStatements.ACTION_WORK_IN_AREA.actionActivate(logic, mapParameter);
                    require(helper, station.gateArea(robot) == null && robot.workArea().contains(outside),
                            "a gate another player configured gives someone's robot no area");
                    require(helper, visitor.mayBeDirectedBy(null), "a robot nobody owns takes any gate's area");
                    logic.setEditor(new GateLogic.Editor(UUID.randomUUID(), true));
                    BCRoboticsStatements.ACTION_WORK_IN_AREA.actionActivate(logic, mapParameter);
                    require(helper, station.gateArea(robot) != null && !robot.workArea().contains(outside),
                            "a gate an operator configured gives anyone's robot its area");
                    logic.setEditor(GateLogic.Editor.of(owner));
                    BCRoboticsStatements.ACTION_WORK_IN_AREA.actionActivate(logic, mapParameter);
                    GateLogic reloaded = new GateLogic(gate, logic.variant);
                    reloaded.readFromNbt(logic.writeToNbt());
                    require(helper, reloaded.editor() != null && owner.getUUID().equals(reloaded.editor().id()),
                            "a gate remembers who configured it when saved");
                    CompoundTag client = new CompoundTag();
                    gate.writeClient(client);
                    require(helper, !client.getCompound("data").contains("editor") && !logic.writeConfigData().contains("editor"),
                            "who configured a gate is neither sent to clients nor copied");
                    WorkArea area = robot.workArea();
                    require(helper, area.contains(helper.absolutePos(new BlockPos(1, 1, 1))) && !area.contains(helper.absolutePos(new BlockPos(5, 1, 1))),
                            "Work in Area gives the robots that live here the map's area");
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    require(helper, !station.isForbidden() && !station.isDroppingInPipe() && station.gateArea(robot) == null,
                            "the actions lapse once the gate stops holding them");
                    require(helper, BCRoboticsStatements.TRIGGER_STATION_LINKED.isTriggerActive(logic, NO_PARAMETERS), "the station stays linked");
                })
                .thenExecute(() -> done(helper, robot, visitor))
                .thenSucceed();
    }
}
