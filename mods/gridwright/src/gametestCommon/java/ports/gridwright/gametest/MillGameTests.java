package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import ports.gridwright.power.MillBlockEntity;
import ports.gridwright.power.MillKind;
import ports.gridwright.registry.ModPower;

import static ports.gridwright.gametest.GameTestSupport.Owner;
import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.connectedPlayer;
import static ports.gridwright.gametest.GameTestSupport.owned;
import static ports.gridwright.gametest.GameTestSupport.place;
import static ports.gridwright.gametest.GameTestSupport.snapshot;

/** The fire, lava, manual and dragon egg mills. */
public class MillGameTests {

    private static final double EPSILON = 1e-6;

    private static MillBlockEntity mill(GameTestHelper helper, MillKind kind, BlockPos pos, Owner owner) {
        return owned(place(helper, ModPower.MILLS.get(kind), pos), owner);
    }

    private static void near(double actual, double expected, String what) {
        check(Math.abs(actual - expected) < EPSILON, what + ": expected " + expected + ", got " + actual);
    }

    /** A lava source in a stone and glass cup beside {@code mill}, so it cannot flow anywhere. */
    private static void lavaCup(GameTestHelper helper, BlockPos mill, Direction side) {
        BlockPos lava = mill.relative(side);
        helper.setBlock(lava.below(), Blocks.STONE);
        helper.setBlock(lava.above(), Blocks.GLASS);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos beside = lava.relative(direction);
            if (!beside.equals(mill)) helper.setBlock(beside, Blocks.GLASS);
        }
        helper.setBlock(lava, Blocks.LAVA);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void fireMillNeedsFireBelow(GameTestHelper helper) {
        Owner owner = Owner.random("gwFi");
        BlockPos campfire = new BlockPos(2, 1, 2);
        helper.setBlock(campfire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        MillBlockEntity overCampfire = mill(helper, MillKind.FIRE, campfire.above(), owner);
        helper.setBlock(new BlockPos(5, 0, 5), Blocks.SOUL_SOIL);
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.SOUL_FIRE);
        MillBlockEntity overSoulFire = mill(helper, MillKind.FIRE, new BlockPos(5, 2, 5), owner);
        MillBlockEntity cold = mill(helper, MillKind.FIRE, new BlockPos(1, 4, 5), owner);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    near(overCampfire.output(), MillKind.FIRE_GP, "over a lit campfire");
                    near(overSoulFire.output(), MillKind.FIRE_GP, "over soul fire");
                    near(cold.output(), 0, "over air");
                    check(snapshot(helper, owner.gridKey()).capacity() > MillKind.FIRE_GP, "Two fire mills feed the grid");
                })
                .thenExecute(() -> helper.setBlock(campfire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false)))
                .thenWaitUntil(() -> near(overCampfire.output(), 0, "over a doused campfire"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void lavaMillCountsLavaSources(GameTestHelper helper) {
        Owner owner = Owner.random("gwLa");
        BlockPos pos = new BlockPos(3, 2, 3);
        MillBlockEntity mill = mill(helper, MillKind.LAVA, pos, owner);
        lavaCup(helper, pos, Direction.NORTH);
        lavaCup(helper, pos, Direction.SOUTH);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    near(mill.output(), 2 * MillKind.LAVA_GP_PER_SIDE, "two lava sides");
                    near(snapshot(helper, owner.gridKey()).capacity(), 2 * MillKind.LAVA_GP_PER_SIDE, "capacity");
                })
                .thenExecute(() -> helper.setBlock(pos.north(), Blocks.GLASS))
                .thenWaitUntil(() -> near(mill.output(), MillKind.LAVA_GP_PER_SIDE, "one lava side"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void manualMillRunsOnlyWhileWound(GameTestHelper helper) {
        Owner owner = Owner.random("gwMa");
        MillBlockEntity mill = mill(helper, MillKind.MANUAL, new BlockPos(2, 1, 2), owner);
        MillBlockEntity fire = mill(helper, MillKind.FIRE, new BlockPos(5, 1, 5), owner);
        // A player who is not in the server's player list (as every machine's fake player is) cannot crank.
        ServerPlayer machine = connectedPlayer(helper, owner);
        check(!MillBlockEntity.isCrankingPlayer(machine), "An unlisted player counts as cranking");
        check(!mill.crank(machine) && mill.wound() == 0, "A fake player turned the manual mill");
        check(!fire.crank(machine), "Only the manual mill has a crank");
        for (int i = 0; i < 10; i++) mill.windUp();
        check(mill.wound() == MillBlockEntity.MAX_WOUND, "Winding is capped: " + mill.wound());
        helper.startSequence()
                .thenWaitUntil(() -> {
                    near(mill.output(), MillKind.MANUAL_GP, "while wound");
                    near(snapshot(helper, owner.gridKey()).capacity(), MillKind.MANUAL_GP, "capacity while wound");
                })
                .thenWaitUntil(() -> {
                    check(mill.wound() == 0, "Still wound: " + mill.wound());
                    near(mill.output(), 0, "after running down");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void dragonEggMillNeedsTheEgg(GameTestHelper helper) {
        Owner owner = Owner.random("gwDe");
        BlockPos pos = new BlockPos(3, 1, 3);
        MillBlockEntity mill = mill(helper, MillKind.DRAGON_EGG, pos, owner);
        helper.startSequence()
                .thenExecuteFor(5, () -> near(mill.output(), 0, "without an egg"))
                .thenExecute(() -> helper.setBlock(pos.above(), Blocks.DRAGON_EGG))
                .thenWaitUntil(() -> {
                    near(mill.output(), MillKind.DRAGON_EGG_GP, "with the egg");
                    near(snapshot(helper, owner.gridKey()).capacity(), MillKind.DRAGON_EGG_GP, "capacity");
                })
                .thenExecute(() -> helper.setBlock(pos.above(), Blocks.AIR))
                .thenWaitUntil(() -> near(mill.output(), 0, "after the egg is taken"))
                .thenSucceed();
    }
}
