package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.grid.GridManager;
import ports.gridwright.grid.GridSnapshot;
import ports.gridwright.grid.ProducerBlockEntity;
import ports.gridwright.machine.ResonatorBlockEntity;
import ports.gridwright.machine.ResonatorBlockEntity.Status;
import ports.gridwright.registry.ModBlocks;
import ports.gridwright.registry.ModItems;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static ports.gridwright.gametest.GameTestSupport.Owner;
import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.grid;
import static ports.gridwright.gametest.GameTestSupport.place;
import static ports.gridwright.gametest.GameTestSupport.player;
import static ports.gridwright.gametest.GameTestSupport.producer;
import static ports.gridwright.gametest.GameTestSupport.resonator;
import static ports.gridwright.gametest.GameTestSupport.snapshot;
import static ports.gridwright.gametest.GameTestSupport.waterColumn;

/** Grid Power accounting: ownership, teams, capacity, demand, overload and diminishing returns. */
public class GridGameTests {

    private static final BlockPos MILL = new BlockPos(3, 2, 3);
    private static final BlockPos RESONATOR = new BlockPos(6, 2, 6);
    private static final BlockPos SECOND_RESONATOR = new BlockPos(0, 2, 6);
    private static final double EPSILON = 1e-9;

    /** A water mill with flowing water on two sides: 6 GP once it has measured the water. */
    private static ProducerBlockEntity twoSidedMill(GameTestHelper helper, Owner owner) {
        ProducerBlockEntity mill = producer(helper, ModBlocks.WATER_MILL, MILL, owner);
        waterColumn(helper, MILL, Direction.EAST);
        waterColumn(helper, MILL, Direction.WEST);
        return mill;
    }

    private static void near(double actual, double expected, String what) {
        check(Math.abs(actual - expected) < EPSILON, what + ": expected " + expected + ", got " + actual);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void waterMillPowersResonator(GameTestHelper helper) {
        Owner owner = Owner.random("gwA");
        ProducerBlockEntity mill = twoSidedMill(helper, owner);
        ResonatorBlockEntity resonator = resonator(helper, RESONATOR, owner);
        resonator.setItem(0, new ItemStack(Items.REDSTONE, 2));
        helper.succeedWhen(() -> {
            near(mill.output(), 6.0, "two-sided water mill");
            check(resonator.getItem(1).is(ModItems.GRID_CRYSTAL.get()), "No grid crystal yet; status " + resonator.status());
            GridSnapshot grid = snapshot(helper, owner.gridKey());
            near(grid.capacity(), 6.0, "capacity");
            near(grid.demand(), 4.0, "demand");
            check(grid.producers() == 1 && grid.consumers() == 1, "Members: " + grid);
            check(grid.powered(), "Grid should be powered: " + grid);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void overloadStopsEveryConsumer(GameTestHelper helper) {
        Owner owner = Owner.random("gwB");
        ProducerBlockEntity mill = twoSidedMill(helper, owner);
        ResonatorBlockEntity first = resonator(helper, RESONATOR, owner);
        ResonatorBlockEntity second = resonator(helper, SECOND_RESONATOR, owner);
        first.setItem(0, new ItemStack(Items.REDSTONE, 4));
        second.setItem(0, new ItemStack(Items.REDSTONE, 4));
        helper.startSequence()
                .thenWaitUntil(() -> near(mill.output(), 6.0, "two-sided water mill"))
                .thenIdle(3)
                .thenExecuteFor(40, () -> {
                    GridSnapshot grid = snapshot(helper, owner.gridKey());
                    check(!grid.powered(), "8 GP of demand on 6 GP should overload: " + grid);
                    for (ResonatorBlockEntity resonator : List.of(first, second)) {
                        check(resonator.status() == Status.OVERLOADED, "Expected OVERLOADED, got " + resonator.status());
                        check(resonator.progress() == 0, "An overloaded resonator advanced to " + resonator.progress());
                    }
                })
                .thenExecute(() -> second.setItem(0, ItemStack.EMPTY))
                .thenWaitUntil(() -> {
                    check(second.status() == Status.IDLE, "Emptied resonator should idle: " + second.status());
                    check(first.status() == Status.RUNNING && first.progress() > 0, "The remaining 4 GP should run; status "
                            + first.status() + ", progress " + first.progress());
                    near(snapshot(helper, owner.gridKey()).demand(), 4.0, "demand after one consumer stopped");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void gridsAreSeparatePerOwner(GameTestHelper helper) {
        Owner millOwner = Owner.random("gwC");
        Owner other = Owner.random("gwD");
        ProducerBlockEntity mill = twoSidedMill(helper, millOwner);
        ResonatorBlockEntity resonator = resonator(helper, RESONATOR, other);
        resonator.setItem(0, new ItemStack(Items.REDSTONE));
        helper.startSequence()
                .thenWaitUntil(() -> near(mill.output(), 6.0, "two-sided water mill"))
                .thenIdle(3)
                .thenExecuteFor(40, () -> {
                    near(snapshot(helper, millOwner.gridKey()).capacity(), 6.0, "mill owner's capacity");
                    GridSnapshot others = snapshot(helper, other.gridKey());
                    near(others.capacity(), 0.0, "other player's capacity");
                    near(others.demand(), 4.0, "other player's demand");
                    check(resonator.status() == Status.OVERLOADED && resonator.progress() == 0,
                            "Another player's mill must not power this resonator: " + resonator.status());
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void teamMembersShareOneGrid(GameTestHelper helper) {
        Owner millOwner = Owner.random("gwE");
        Owner teammate = Owner.random("gwF");
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("gw" + millOwner.id().toString().substring(0, 8));
        scoreboard.addPlayerToTeam(millOwner.name(), team);
        scoreboard.addPlayerToTeam(teammate.name(), team);
        ProducerBlockEntity mill = twoSidedMill(helper, millOwner);
        ResonatorBlockEntity resonator = resonator(helper, RESONATOR, teammate);
        resonator.setItem(0, new ItemStack(Items.REDSTONE));
        String teamKey = "team:" + team.getName();
        check(teamKey.equals(mill.gridKey(helper.getLevel().getServer())), "Mill should report to " + teamKey);
        check(teamKey.equals(resonator.gridKey(helper.getLevel().getServer())), "Resonator should report to " + teamKey);
        helper.succeedWhen(() -> {
            check(resonator.getItem(1).is(ModItems.GRID_CRYSTAL.get()), "Teammate's mill should power the resonator: "
                    + resonator.status());
            GridSnapshot shared = snapshot(helper, teamKey);
            check(shared.producers() == 1 && shared.consumers() == 1, "Team grid members: " + shared);
            near(snapshot(helper, millOwner.gridKey()).capacity(), 0.0, "personal grid of a team member");
            scoreboard.removePlayerTeam(team);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void unownedBlocksJoinNoGrid(GameTestHelper helper) {
        ResonatorBlockEntity resonator = resonator(helper, RESONATOR, null);
        resonator.setItem(0, new ItemStack(Items.REDSTONE));
        check(resonator.gridKey(helper.getLevel().getServer()) == null, "Unowned block has a grid");
        helper.startSequence()
                .thenIdle(2)
                .thenExecuteFor(20, () -> check(resonator.status() == Status.NO_OWNER && resonator.progress() == 0,
                        "Unowned resonator: " + resonator.status() + " at " + resonator.progress()))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void placingABlockMakesThePlacerItsOwner(GameTestHelper helper) {
        Owner owner = Owner.random("gwG");
        ServerPlayer player = player(helper, owner);
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.SOLAR_PANEL.get());
        BlockPos absolute = helper.absolutePos(pos);
        BlockState state = helper.getLevel().getBlockState(absolute);
        state.getBlock().setPlacedBy(helper.getLevel(), absolute, state, player, new ItemStack(ModBlocks.SOLAR_PANEL.get()));
        GridBlockEntity member = (GridBlockEntity) helper.getBlockEntity(pos);
        check(owner.id().equals(member.owner()) && owner.name().equals(member.ownerName()), "Owner not recorded: " + member.owner());
        check(owner.gridKey().equals(member.gridKey(helper.getLevel().getServer())), "Wrong grid " + member.gridKey(helper.getLevel().getServer()));

        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("gw" + owner.id().toString().substring(0, 8));
        scoreboard.addPlayerToTeam(owner.name(), team);
        String key = member.gridKey(helper.getLevel().getServer());
        scoreboard.removePlayerTeam(team);
        check(("team:" + team.getName()).equals(key), "A team member's block should join the team grid, got " + key);
        check(owner.gridKey().equals(member.gridKey(helper.getLevel().getServer())), "Leaving the team should restore the personal grid");

        // Ownership survives a save and load.
        GridBlockEntity copy = (GridBlockEntity) place(helper, ModBlocks.SOLAR_PANEL, new BlockPos(4, 2, 2));
        copy.load(member.saveWithoutMetadata());
        check(owner.id().equals(copy.owner()) && owner.name().equals(copy.ownerName()), "Owner lost on reload");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void removingAProducerCutsPower(GameTestHelper helper) {
        Owner owner = Owner.random("gwH");
        twoSidedMill(helper, owner);
        ResonatorBlockEntity resonator = resonator(helper, RESONATOR, owner);
        resonator.setItem(0, new ItemStack(Items.REDSTONE, 4));
        AtomicInteger frozenAt = new AtomicInteger();
        helper.startSequence()
                .thenWaitUntil(() -> check(resonator.progress() > 5, "Resonator not running yet: " + resonator.status()))
                .thenExecute(() -> helper.setBlock(MILL, Blocks.AIR))
                .thenWaitUntil(() -> {
                    near(snapshot(helper, owner.gridKey()).capacity(), 0.0, "capacity after removing the mill");
                    check(resonator.status() == Status.OVERLOADED, "Expected OVERLOADED, got " + resonator.status());
                })
                .thenExecute(() -> frozenAt.set(resonator.progress()))
                .thenExecuteFor(20, () -> check(resonator.progress() == frozenAt.get(),
                        "Progress moved without power: " + resonator.progress()))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void sameTypeProducersHaveDiminishingReturns(GameTestHelper helper) {
        near(GridManager.combine(List.of()), 0.0, "no producers");
        near(GridManager.combine(List.of(4.0)), 4.0, "one producer");
        near(GridManager.combine(List.of(4.0, 4.0)), 4.0 + 4.0 * 32 / 33, "two equal producers");
        // The strongest producer always counts in full, whatever order they report in.
        near(GridManager.combine(List.of(1.0, 6.0)), GridManager.combine(List.of(6.0, 1.0)), "order independence");
        near(GridManager.combine(List.of(1.0, 6.0)), 6.0 + 32.0 / 33, "strongest first");

        // Live: two producers of one type and one of another, reported through the real ledger.
        String key = Owner.random("gwI").gridKey();
        GridManager manager = grid(helper);
        helper.succeedWhen(() -> {
            GridSnapshot last = manager.snapshot(key);
            manager.addProduction(key, "gridwright:test_a", 4.0);
            manager.addProduction(key, "gridwright:test_a", 4.0);
            manager.addProduction(key, "gridwright:test_b", 4.0);
            manager.addDemand(key, 10.0);
            near(last.capacity(), 4.0 + 4.0 * 32 / 33 + 4.0, "mixed capacity");
            near(last.demand(), 10.0, "demand");
            check(last.producers() == 3 && last.consumers() == 1, "Members " + last);
            check(last.powered() == (10.0 <= last.capacity()), "powered flag");
        });
    }
}
