package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.chest;
import static ports.buildcraft.gametest.BCTests.count;
import static ports.buildcraft.gametest.BCTests.pipe;
import static ports.buildcraft.gametest.BCTests.require;
import static ports.buildcraft.gametest.RobotTests.ONE_PARAMETER;
import static ports.buildcraft.gametest.RobotTests.PLAIN;
import static ports.buildcraft.gametest.RobotTests.areaMap;
import static ports.buildcraft.gametest.RobotTests.confine;
import static ports.buildcraft.gametest.RobotTests.deploy;
import static ports.buildcraft.gametest.RobotTests.describe;
import static ports.buildcraft.gametest.RobotTests.gate;
import static ports.buildcraft.gametest.RobotTests.station;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.factory.BCFactory;
import ports.buildcraft.factory.TankBlockEntity;
import ports.buildcraft.robotics.DockingStationPluggable;
import ports.buildcraft.robotics.RobotEntity;
import ports.buildcraft.robotics.board.BreakerBoard;
import ports.buildcraft.robotics.board.FighterBoard;
import ports.buildcraft.robotics.board.PlanterBoard;
import ports.buildcraft.robotics.board.RobotBoardType;
import ports.buildcraft.robotics.board.StripesBoard;
import ports.buildcraft.robotics.statements.BCRoboticsStatements;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlockEntity;

/**
 * Loader-neutral GameTests of the port's phase 2 robot boards: harvester, leaf cutter, shovelman, breaker, farmer,
 * planter, pump, tank (fluid carrier, with the fluid station actions), knight, butcher and stripes. As in
 * {@link RobotTests}, every robot lives at a station in the middle of its arena (with a chest or tank beside the
 * pipe) and is confined to the arena by a gate's "Work in Area" from its stone floor (y 1) up. Soil the robots work
 * on replaces floor blocks, and the robots are discarded when the test ends.
 */
public final class RobotBoardTests {
    private static final BlockPos HOME = new BlockPos(4, 2, 4);
    private static final BlockPos BESIDE_HOME = new BlockPos(4, 1, 4);
    /** Moist farmland, which random ticks cannot dry back to dirt within a test. */
    private static final BlockState FARMLAND = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE);

    private RobotBoardTests() {}

    /** A station on an item pipe at {@link #HOME}, confined to the arena; the block beside the pipe is set by the caller. */
    private static DockingStationPluggable home(GameTestHelper helper) {
        PipeBlockEntity pipe = pipe(helper, HOME, BCTransport.STONE_ITEM);
        DockingStationPluggable station = station(pipe, Direction.UP);
        confine(helper, pipe, Direction.NORTH);
        return station;
    }

    /** As {@link #home}, but confined to the air above the arena's floor, for robots that would dig the floor. */
    private static DockingStationPluggable homeAboveFloor(GameTestHelper helper) {
        PipeBlockEntity pipe = pipe(helper, HOME, BCTransport.STONE_ITEM);
        DockingStationPluggable station = station(pipe, Direction.UP);
        gate(pipe, Direction.NORTH, ONE_PARAMETER, BCRoboticsStatements.ACTION_WORK_IN_AREA, areaMap(helper, new BlockPos(0, 2, 0), new BlockPos(8, 6, 8)));
        return station;
    }

    /** Spawns a mob without AI on a stone block at {@code pos}, so that it stays in the arena whatever happens to the floor. */
    private static <T extends net.minecraft.world.entity.Mob> T mob(GameTestHelper helper, EntityType<T> type, BlockPos pos) {
        helper.setBlock(pos, Blocks.STONE);
        return helper.spawnWithNoFreeWill(type, pos.above());
    }

    private static TankBlockEntity tank(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, BCFactory.TANK.get());
        return (TankBlockEntity) helper.getBlockEntity(pos);
    }

    /** Surrounds {@code pos} with stone on its four sides and below, so a fluid placed there stays put. */
    private static void basin(GameTestHelper helper, BlockPos pos) {
        for (Direction side : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN}) {
            helper.setBlock(pos.relative(side), Blocks.STONE);
        }
    }

    /** The harvester picks mature crops by hand and brings the harvest home, leaving young crops to grow. */
    public static void harvesterPicksMatureCrops(GameTestHelper helper) {
        ChestBlockEntity chest = chest(helper, BESIDE_HOME);
        DockingStationPluggable station = home(helper);
        BlockPos ripe = new BlockPos(2, 2, 2), young = new BlockPos(6, 2, 6);
        helper.setBlock(ripe.below(), FARMLAND);
        helper.setBlock(young.below(), FARMLAND);
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        helper.setBlock(ripe, wheat.getStateForAge(wheat.getMaxAge()));
        helper.setBlock(young, wheat.getStateForAge(0));
        require(helper, BreakerBoard.isMatureCrop(wheat.getStateForAge(wheat.getMaxAge())) && !BreakerBoard.isMatureCrop(wheat.getStateForAge(3))
                && BreakerBoard.isMatureCrop(Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, 3)),
                "the harvester knows ripe crops");
        RobotEntity robot = deploy(helper, helper.makeMockPlayer(), HOME, station, RobotBoardType.HARVESTER);
        helper.succeedWhen(() -> {
            require(helper, helper.getBlockState(ripe).isAir(), "the harvester picks the ripe wheat; " + describe(helper, robot));
            require(helper, count(chest, Items.WHEAT) >= 1, "the harvester brings the wheat home; " + describe(helper, robot));
            require(helper, helper.getBlockState(young).is(Blocks.WHEAT), "the harvester leaves the young wheat");
            robot.discard();
        });
    }

    /** The leaf cutter takes shears from home when there are some, cuts the leaves in its area and brings them home. */
    public static void leafCutterCutsLeaves(GameTestHelper helper) {
        ChestBlockEntity chest = chest(helper, BESIDE_HOME, new ItemStack(Items.SHEARS));
        DockingStationPluggable station = home(helper);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockPos[] targets = {new BlockPos(2, 1, 2), new BlockPos(6, 3, 6)};
        for (BlockPos pos : targets) helper.setBlock(pos, leaves);
        RobotEntity robot = deploy(helper, helper.makeMockPlayer(), HOME, station, RobotBoardType.LEAF_CUTTER);
        helper.succeedWhen(() -> {
            for (BlockPos pos : targets) require(helper, helper.getBlockState(pos).isAir(), "the leaf cutter cuts the leaves at " + pos + "; " + describe(helper, robot));
            require(helper, robot.tool().is(Items.SHEARS) && count(chest, Items.SHEARS) == 0, "the leaf cutter works with the chest's shears");
            require(helper, count(chest, Items.OAK_LEAVES) == 2, "sheared leaves drop themselves and come home, chest has "
                    + count(chest, Items.OAK_LEAVES) + "; " + describe(helper, robot));
            robot.discard();
        });
    }

    /** The shovelman digs dirt and clay open to air with a shovel from home, and leaves ground it has no business with. */
    public static void shovelmanDigsOpenGround(GameTestHelper helper) {
        ChestBlockEntity chest = chest(helper, BESIDE_HOME, new ItemStack(Items.IRON_SHOVEL));
        DockingStationPluggable station = home(helper);
        BlockPos dirt = new BlockPos(2, 1, 2), clay = new BlockPos(6, 2, 6), planks = new BlockPos(2, 1, 6);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.setBlock(clay, Blocks.CLAY);
        helper.setBlock(planks, Blocks.OAK_PLANKS);
        require(helper, BreakerBoard.isShovelGround(Blocks.GRAVEL.defaultBlockState()) && !BreakerBoard.isShovelGround(Blocks.FARMLAND.defaultBlockState())
                && BreakerBoard.isShovel(new ItemStack(Items.WOODEN_SHOVEL)) && !BreakerBoard.isShovel(new ItemStack(Items.IRON_PICKAXE)),
                "the shovelman knows its ground and its tools");
        RobotEntity robot = deploy(helper, helper.makeMockPlayer(), HOME, station, RobotBoardType.SHOVELMAN);
        helper.succeedWhen(() -> {
            require(helper, helper.getBlockState(dirt).isAir() && helper.getBlockState(clay).isAir(), "the shovelman digs the dirt and clay; " + describe(helper, robot));
            require(helper, count(chest, Items.DIRT) == 1 && count(chest, Items.CLAY_BALL) == 4,
                    "the shovelman brings the dirt and clay home; " + describe(helper, robot));
            require(helper, helper.getBlockState(planks).is(Blocks.OAK_PLANKS), "the shovelman leaves the planks");
            robot.discard();
        });
    }

    /**
     * The breaker digs stone open to air with a pickaxe from home and brings the cobblestone back, and leaves stone
     * protection refuses its owner. Its area starts above the arena's stone floor.
     */
    public static void breakerDigsOpenStoneItMayBreak(GameTestHelper helper) {
        ChestBlockEntity chest = chest(helper, BESIDE_HOME, new ItemStack(Items.STONE_PICKAXE));
        DockingStationPluggable station = homeAboveFloor(helper);
        BlockPos open = new BlockPos(2, 2, 2), refused = new BlockPos(6, 2, 2);
        helper.setBlock(open, Blocks.STONE);
        helper.setBlock(refused, Blocks.STONE);
        TestProtection.refuse(helper, refused);
        require(helper, BreakerBoard.isStone(Blocks.DEEPSLATE.defaultBlockState()) && BreakerBoard.isStone(Blocks.NETHERRACK.defaultBlockState())
                && !BreakerBoard.isStone(Blocks.COAL_ORE.defaultBlockState()), "the breaker knows stone from ore");
        Player player = helper.makeMockPlayer();
        RobotEntity robot = deploy(helper, player, HOME, station, RobotBoardType.BREAKER);
        helper.succeedWhen(() -> {
            require(helper, helper.getBlockState(open).isAir(), "the breaker digs the open stone; " + describe(helper, robot));
            require(helper, count(chest, Items.COBBLESTONE) == 1, "the breaker brings the cobblestone home; " + describe(helper, robot));
            require(helper, player.getGameProfile().getName().equals(TestProtection.refusedPlayer(helper, refused)),
                    "the break event names the robot's owner, was " + TestProtection.refusedPlayer(helper, refused));
            require(helper, helper.getBlockState(refused).is(Blocks.STONE), "the breaker leaves the protected stone");
            TestProtection.allow(helper, refused);
            robot.discard();
        });
    }

    /** The farmer tills dirt and grass with air above into farmland with a hoe from home, and leaves dirt protection refuses. */
    public static void farmerTillsDirtItMayChange(GameTestHelper helper) {
        chest(helper, BESIDE_HOME, new ItemStack(Items.IRON_HOE));
        DockingStationPluggable station = home(helper);
        BlockPos dirt = new BlockPos(2, 1, 2), grass = new BlockPos(6, 1, 6), covered = new BlockPos(6, 1, 2), refused = new BlockPos(2, 1, 6);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.setBlock(grass, Blocks.GRASS_BLOCK);
        helper.setBlock(covered, Blocks.DIRT);
        helper.setBlock(covered.above(), Blocks.STONE);
        helper.setBlock(refused, Blocks.DIRT);
        TestProtection.refuse(helper, refused);
        Player player = helper.makeMockPlayer();
        RobotEntity robot = deploy(helper, player, HOME, station, RobotBoardType.FARMER);
        helper.succeedWhen(() -> {
            require(helper, helper.getBlockState(dirt).is(Blocks.FARMLAND) && helper.getBlockState(grass).is(Blocks.FARMLAND),
                    "the farmer tills the dirt and the grass; " + describe(helper, robot));
            require(helper, robot.tool().is(Items.IRON_HOE) && robot.tool().getDamageValue() >= 2, "the farmer's hoe wears as it tills");
            require(helper, player.getGameProfile().getName().equals(TestProtection.refusedPlayer(helper, refused)),
                    "the farmer asks protection for its owner, was " + TestProtection.refusedPlayer(helper, refused));
            require(helper, helper.getBlockState(refused).is(Blocks.DIRT), "the farmer leaves the protected dirt");
            require(helper, helper.getBlockState(covered).is(Blocks.DIRT), "the farmer leaves dirt with no air above");
            TestProtection.allow(helper, refused);
            robot.discard();
        });
    }

    /**
     * The planter takes seeds and saplings from home and plants them, seeds on farmland and saplings on dirt, one
     * item a plant; with nowhere left for one kind it brings it home and fetches the other.
     */
    public static void planterPlantsSeedsAndSaplings(GameTestHelper helper) {
        ChestBlockEntity chest = chest(helper, BESIDE_HOME, new ItemStack(Items.WHEAT_SEEDS, 3), new ItemStack(Items.OAK_SAPLING, 2));
        DockingStationPluggable station = home(helper);
        BlockPos farmland = new BlockPos(2, 1, 2), dirt = new BlockPos(6, 1, 6);
        helper.setBlock(farmland, FARMLAND);
        helper.setBlock(dirt, Blocks.DIRT);
        require(helper, PlanterBoard.isPlantable(new ItemStack(Items.MELON_SEEDS)) && !PlanterBoard.isPlantable(new ItemStack(Items.DIRT)),
                "the planter knows plantables");
        // Neighbouring tests may shade the arena, and wheat needs light: a lamp beside the farmland lets it grow.
        helper.setBlock(farmland.west(), Blocks.GLOWSTONE);
        RobotEntity robot = deploy(helper, helper.makeMockPlayer(), HOME, station, RobotBoardType.PLANTER);
        helper.succeedWhen(() -> {
            require(helper, helper.getBlockState(farmland.above()).is(Blocks.WHEAT), "the planter sows the farmland, which is " + helper.getBlockState(farmland)
                    + " under " + helper.getBlockState(farmland.above()) + ", chest has " + count(chest, Items.WHEAT_SEEDS) + " seeds and "
                    + count(chest, Items.OAK_SAPLING) + " saplings; " + describe(helper, robot));
            require(helper, helper.getBlockState(dirt.above()).is(Blocks.OAK_SAPLING), "the planter plants the sapling on the dirt; " + describe(helper, robot));
            require(helper, count(chest, Items.WHEAT_SEEDS) == 2 && count(chest, Items.OAK_SAPLING) == 1 && robot.isInventoryEmpty(),
                    "one item makes each plant and the rest goes home; " + describe(helper, robot));
            robot.discard();
        });
    }

    /**
     * The pump drains the fluid source blocks in its area into its tank and empties it into the tank beside its
     * home station; it leaves a source protection refuses its owner.
     */
    public static void pumpDrainsSourcesIntoTanksAtHome(GameTestHelper helper) {
        TankBlockEntity tank = tank(helper, BESIDE_HOME);
        DockingStationPluggable station = home(helper);
        BlockPos[] sources = {new BlockPos(2, 2, 2), new BlockPos(6, 2, 6)};
        BlockPos refused = new BlockPos(2, 2, 6);
        for (BlockPos pos : List.of(sources[0], sources[1], refused)) {
            basin(helper, pos);
            helper.setBlock(pos, Blocks.WATER);
        }
        TestProtection.refuse(helper, refused);
        Player player = helper.makeMockPlayer();
        RobotEntity robot = deploy(helper, player, HOME, station, RobotBoardType.PUMP);
        helper.succeedWhen(() -> {
            for (BlockPos pos : sources) {
                require(helper, helper.getBlockState(pos).isAir(), "the pump drains the water at " + pos + ", found " + helper.getBlockState(pos)
                        + ", refused for " + TestProtection.refusedPlayer(helper, pos) + "; tank has " + tank.tank().amount() + ", robot "
                        + robot.tank().amount() + "; " + describe(helper, robot));
            }
            require(helper, tank.tank().fluid() == Fluids.WATER && tank.tank().amount() == 2000 && robot.tank().isEmpty(),
                    "the pump empties two buckets into the tank at home, tank has " + tank.tank().amount() + ", robot " + robot.tank().amount());
            require(helper, helper.getBlockState(refused).is(Blocks.WATER), "the pump leaves the protected water");
            require(helper, player.getGameProfile().getName().equals(TestProtection.refusedPlayer(helper, refused)),
                    "the pump asks protection for its owner, was " + TestProtection.refusedPlayer(helper, refused));
            TestProtection.allow(helper, refused);
            robot.discard();
        });
    }

    /**
     * The tank robot fills up at a station whose gate provides fluids, from the tank beside its pipe, and empties at a
     * station whose gate accepts fluids. A Provide Fluids filter of a lava bucket lets no water through.
     */
    public static void tankRobotMovesFluidsBetweenStations(GameTestHelper helper) {
        DockingStationPluggable homeStation = home(helper);

        TankBlockEntity source = tank(helper, new BlockPos(1, 1, 1));
        source.tank().set(Fluids.WATER, 4000);
        PipeBlockEntity provider = pipe(helper, new BlockPos(1, 2, 1), BCTransport.STONE_ITEM);
        DockingStationPluggable providerStation = station(provider, Direction.UP);
        providerStation.actionProvideFluids(List.of(new ItemStack(Items.LAVA_BUCKET)));
        require(helper, providerStation.isProvidingFluids() && !providerStation.canProvideFluid(null), "a lava filter lets no water through");
        providerStation.actionProvideFluids(List.of(new ItemStack(Items.WATER_BUCKET)));
        require(helper, providerStation.canProvideFluid(null), "a water filter lets water through");
        gate(provider, Direction.NORTH, ONE_PARAMETER, BCRoboticsStatements.ACTION_PROVIDE_FLUIDS, new ItemStack(Items.WATER_BUCKET));

        TankBlockEntity target = tank(helper, new BlockPos(7, 1, 7));
        PipeBlockEntity acceptor = pipe(helper, new BlockPos(7, 2, 7), BCTransport.STONE_ITEM);
        station(acceptor, Direction.UP);
        gate(acceptor, Direction.NORTH, PLAIN, BCRoboticsStatements.ACTION_ACCEPT_FLUIDS);

        RobotEntity robot = deploy(helper, helper.makeMockPlayer(), HOME, homeStation, RobotBoardType.FLUID_CARRIER);
        helper.succeedWhen(() -> {
            require(helper, target.tank().fluid() == Fluids.WATER && target.tank().amount() == 4000 && source.tank().isEmpty(),
                    "the tank robot moves the water, target has " + target.tank().amount() + ", source " + source.tank().amount()
                            + ", robot " + robot.tank().amount() + "; " + describe(helper, robot));
            robot.discard();
        });
    }

    /** The knight fights a hostile mob with a sword from home, as its owner, and spares one protection will not let it attack. */
    public static void knightFightsMobsItMayAttack(GameTestHelper helper) {
        chest(helper, BESIDE_HOME, new ItemStack(Items.IRON_SWORD));
        DockingStationPluggable station = home(helper);
        Husk prey = mob(helper, EntityType.HUSK, new BlockPos(2, 1, 2));
        Husk spared = mob(helper, EntityType.HUSK, new BlockPos(6, 1, 6));
        Cow cow = mob(helper, EntityType.COW, new BlockPos(2, 1, 6));
        TestProtection.refuseAttacks(helper, spared);
        require(helper, FighterBoard.attackDamage(new ItemStack(Items.IRON_SWORD), prey) == 6 && FighterBoard.attackTicks(new ItemStack(Items.IRON_SWORD)) == 13,
                "a strike with an iron sword deals 6 damage every 13 ticks, as a player's full swing");
        Player player = helper.makeMockPlayer();
        RobotEntity robot = deploy(helper, player, HOME, station, RobotBoardType.KNIGHT);
        helper.succeedWhen(() -> {
            require(helper, !prey.isAlive(), "the knight kills the husk, health " + prey.getHealth() + "; " + describe(helper, robot));
            require(helper, player.getGameProfile().getName().equals(TestProtection.refusedAttacker(spared)),
                    "the attack event names the robot's owner, was " + TestProtection.refusedAttacker(spared));
            require(helper, spared.isAlive() && spared.getHealth() == spared.getMaxHealth(), "the knight spares the protected husk");
            require(helper, cow.isAlive() && cow.getHealth() == cow.getMaxHealth(), "the knight leaves animals alone");
            require(helper, robot.tool().is(Items.IRON_SWORD) && robot.tool().getDamageValue() > 0, "the knight's sword wears");
            TestProtection.allowAttacks(spared);
            robot.discard();
            spared.discard();
            cow.discard();
        });
    }

    /** The butcher fights an adult animal with an axe from home, and spares calves and monsters. */
    public static void butcherFightsAdultAnimals(GameTestHelper helper) {
        chest(helper, BESIDE_HOME, new ItemStack(Items.IRON_AXE));
        DockingStationPluggable station = home(helper);
        Cow adult = mob(helper, EntityType.COW, new BlockPos(2, 1, 2));
        Cow calf = mob(helper, EntityType.COW, new BlockPos(6, 1, 6));
        calf.setAge(-24000);
        Husk husk = mob(helper, EntityType.HUSK, new BlockPos(2, 1, 6));
        require(helper, FighterBoard.isLivestock(adult) && !FighterBoard.isLivestock(calf) && !FighterBoard.isLivestock(husk),
                "the butcher knows livestock");
        Horse horse = EntityType.HORSE.create(helper.getLevel());
        require(helper, FighterBoard.isLivestock(horse), "an untamed horse is livestock");
        horse.setTamed(true);
        require(helper, !FighterBoard.isLivestock(horse), "a tamed horse is spared");
        RobotEntity robot = deploy(helper, helper.makeMockPlayer(), HOME, station, RobotBoardType.BUTCHER);
        helper.succeedWhen(() -> {
            require(helper, !adult.isAlive(), "the butcher kills the cow, health " + adult.getHealth() + "; " + describe(helper, robot));
            require(helper, calf.isAlive() && husk.isAlive() && husk.getHealth() == husk.getMaxHealth(), "the butcher spares the calf and the husk");
            require(helper, robot.tool().is(Items.IRON_AXE), "the butcher fights with the chest's axe");
            robot.discard();
            calf.discard();
            husk.discard();
        });
    }

    /**
     * The stripes robot takes items from home and uses them as a stripes pipe would: blocks are placed on free spots
     * in its area, never on a block entity such as its home's chest or pipe.
     */
    public static void stripesRobotUsesItems(GameTestHelper helper) {
        ChestBlockEntity chest = chest(helper, BESIDE_HOME, new ItemStack(Items.MOSSY_COBBLESTONE, 2));
        DockingStationPluggable station = home(helper);
        require(helper, !StripesBoard.isFreeSpot(helper.getLevel(), helper.absolutePos(HOME.above()))
                && StripesBoard.isFreeSpot(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2))), "the stripes robot builds on the floor but never on its home's pipe");
        RobotEntity robot = deploy(helper, helper.makeMockPlayer(), HOME, station, RobotBoardType.STRIPES);
        helper.succeedWhen(() -> {
            int placed = 0;
            for (BlockPos pos : BlockPos.betweenClosed(0, 1, 0, 8, 6, 8)) {
                if (helper.getBlockState(pos).is(Blocks.MOSSY_COBBLESTONE)) placed++;
            }
            require(helper, placed == 2 && count(chest, Items.MOSSY_COBBLESTONE) == 0 && robot.isInventoryEmpty(),
                    "the stripes robot places both blocks, placed " + placed + "; " + describe(helper, robot));
            require(helper, helper.getBlockState(HOME.above()).isAir(), "nothing is placed on the home pipe");
            robot.discard();
        });
    }

}
