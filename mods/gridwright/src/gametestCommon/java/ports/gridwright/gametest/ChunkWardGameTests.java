package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ports.gridwright.automation.ChunkWardBlockEntity;
import ports.gridwright.automation.ChunkWardBlockEntity.Status;
import ports.gridwright.automation.ChunkWards;
import ports.gridwright.item.GoldenLassoItem;
import ports.gridwright.power.MillKind;
import ports.gridwright.registry.ModAutomation;
import ports.gridwright.registry.ModPower;
import ports.gridwright.registry.ModUtilities;

import java.util.List;

import static ports.gridwright.gametest.GameTestSupport.Owner;
import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.connectedPlayer;
import static ports.gridwright.gametest.GameTestSupport.owned;
import static ports.gridwright.gametest.GameTestSupport.place;
import static ports.gridwright.gametest.GameTestSupport.snapshot;

/**
 * The chunk-loading ward: the villager contract, Grid Power, the per-player limit, letting go when
 * unpaid or broken, the contract kept in the dropped item, and the saved list that restores tickets.
 * Each test runs in its own batch because the limit game rule is server-wide.
 */
public class ChunkWardGameTests {

    private static final BlockPos WARD = new BlockPos(2, 2, 2);
    private static final BlockPos MILL = new BlockPos(5, 2, 5);

    private static ChunkWardBlockEntity ward(GameTestHelper helper, BlockPos pos, Owner owner) {
        return owned(place(helper, ModAutomation.CHUNK_WARD, pos), owner);
    }

    /** A dragon egg mill with its egg: 64 GP, enough for one ward. */
    private static void power(GameTestHelper helper, Owner owner) {
        owned(place(helper, ModPower.MILLS.get(MillKind.DRAGON_EGG), MILL), owner);
        helper.setBlock(MILL.above(), Blocks.DRAGON_EGG);
    }

    private static boolean active(GameTestHelper helper, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        return ChunkWards.get(level.getServer()).isActive(level, helper.absolutePos(pos));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "gridwright_chunk_ward_contract")
    public static void wardNeedsAVillagerContractAndPower(GameTestHelper helper) {
        Owner owner = Owner.random("gwCw");
        ServerPlayer player = connectedPlayer(helper, owner);
        ChunkWardBlockEntity ward = ward(helper, WARD, owner);
        ItemStack lasso = new ItemStack(ModUtilities.GOLDEN_LASSO.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, lasso);
        BlockPos absolute = helper.absolutePos(WARD);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        helper.startSequence()
                .thenExecuteFor(5, () -> {
                    check(ward.status() == Status.NO_CONTRACT && !active(helper, WARD), "Loading without a contract: " + ward.status());
                    check(snapshot(helper, owner.gridKey()).demand() == 0, "An uncontracted ward drew GP");
                })
                .thenExecute(() -> {
                    // A cow will not sign; it stays in the lasso.
                    GoldenLassoItem.capture(lasso, player, helper.spawnWithNoFreeWill(EntityType.COW, 5, 2, 1));
                    helper.getLevel().getBlockState(absolute).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
                    check(!ward.contracted() && GoldenLassoItem.holdsMob(lasso), "A cow signed the contract");
                    GoldenLassoItem.empty(lasso);
                    Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 5, 2, 1);
                    check(GoldenLassoItem.capture(lasso, player, villager), "The lasso should take a villager");
                    helper.getLevel().getBlockState(absolute).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
                    check(ward.contracted() && !GoldenLassoItem.holdsMob(lasso), "The villager should sign and be used up");
                })
                .thenWaitUntil(() -> check(ward.status() == Status.UNPOWERED && !active(helper, WARD),
                        "Loading without power: " + ward.status()))
                .thenExecute(() -> power(helper, owner))
                .thenWaitUntil(() -> {
                    check(ward.status() == Status.LOADING && active(helper, WARD), "Not loading with power: " + ward.status());
                    check(snapshot(helper, owner.gridKey()).demand() == ChunkWardBlockEntity.GP, "Demand "
                            + snapshot(helper, owner.gridKey()).demand());
                })
                .thenExecute(() -> helper.destroyBlock(WARD))
                .thenExecute(() -> check(!active(helper, WARD), "A broken ward still holds its chunks"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "gridwright_chunk_ward_unpaid")
    public static void unpaidWardLetsGoAfterItsGrace(GameTestHelper helper) {
        Owner owner = Owner.random("gwCu");
        ChunkWardBlockEntity ward = ward(helper, WARD, owner);
        ward.contract();
        power(helper, owner);
        long[] stoppedAt = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> check(active(helper, WARD), "Not loading: " + ward.status()))
                .thenExecute(() -> {
                    helper.setBlock(MILL.above(), Blocks.AIR);
                    stoppedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> check(ward.status() == Status.UNPOWERED, "Still paid: " + ward.status()))
                .thenExecuteFor(ChunkWardBlockEntity.GRACE_TICKS - 20, () -> check(active(helper, WARD),
                        "Let go before the grace ran out, after " + (helper.getTick() - stoppedAt[0]) + " ticks"))
                .thenWaitUntil(() -> check(!active(helper, WARD), "Still holding chunks it cannot pay for"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "gridwright_chunk_ward_limit")
    public static void wardsPerPlayerFollowTheGameRule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameRules.IntegerValue rule = level.getGameRules().getRule(ChunkWards.LIMIT);
        int before = rule.get();
        rule.set(1, level.getServer());
        Owner owner = Owner.random("gwCl");
        ChunkWardBlockEntity first = ward(helper, WARD, owner);
        ChunkWardBlockEntity second = ward(helper, new BlockPos(2, 2, 5), owner);
        first.contract();
        second.contract();
        // Two eggs: enough for both wards, so only the rule can stop the second.
        power(helper, owner);
        owned(place(helper, ModPower.MILLS.get(MillKind.DRAGON_EGG), new BlockPos(6, 2, 2)), owner);
        helper.setBlock(new BlockPos(6, 3, 2), Blocks.DRAGON_EGG);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    check(first.status() == Status.LOADING || second.status() == Status.LOADING, "Neither ward loads");
                    check(first.status() == Status.LIMIT || second.status() == Status.LIMIT,
                            "Both wards load past a limit of one: " + first.status() + ", " + second.status());
                    check(ChunkWards.get(level.getServer()).activeFor(owner.id()) == 1, "Active wards");
                })
                .thenExecute(() -> rule.set(before, level.getServer()))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "gridwright_chunk_ward_drops")
    public static void droppedWardKeepsItsContract(GameTestHelper helper) {
        ChunkWardBlockEntity ward = ward(helper, WARD, Owner.random("gwCd"));
        ward.contract();
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(WARD);
        List<ItemStack> drops = Block.getDrops(level.getBlockState(absolute), level, absolute, ward);
        check(drops.size() == 1 && drops.get(0).is(ModAutomation.CHUNK_WARD_ITEM.get()), "Drops " + drops);
        CompoundTag data = BlockItem.getBlockEntityData(drops.get(0));
        check(data != null && data.getBoolean("Contracted"), "The dropped ward lost its contract: " + drops.get(0).getTag());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "gridwright_chunk_ward_saved")
    public static void savedWardsSurviveARestart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Owner owner = Owner.random("gwCs");
        ChunkWards wards = new ChunkWards();
        BlockPos pos = helper.absolutePos(WARD);
        wards.activate(level, pos, owner.id());
        ChunkWards loaded = ChunkWards.load(wards.save(new CompoundTag()));
        check(loaded.isActive(level, pos) && loaded.activeFor(owner.id()) == 1, "The saved list lost the ward");
        // Release the tickets this test took.
        wards.deactivate(level, pos);
        check(!wards.isActive(level, pos), "Deactivated ward still listed");
        helper.succeed();
    }
}
