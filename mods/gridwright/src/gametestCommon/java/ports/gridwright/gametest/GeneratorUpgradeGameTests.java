package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ports.gridwright.machine.GeneratorBlockEntity;
import ports.gridwright.machine.GeneratorKind;
import ports.gridwright.platform.Platform;
import ports.gridwright.power.Overclock;
import ports.gridwright.registry.ModPower;
import ports.gridwright.transfer.TransferPlatform;

import static ports.gridwright.gametest.GameTestSupport.Owner;
import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.connectedPlayer;
import static ports.gridwright.gametest.GameTestSupport.generator;
import static ports.gridwright.gametest.GameTestSupport.platform;

/** Phase 2d generator additions: overclock upgrades and piped lava (including from transfer nodes) for the lava generator. */
public class GeneratorUpgradeGameTests {

    private static final BlockPos POS = new BlockPos(2, 1, 2);

    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        BlockState state = helper.getLevel().getBlockState(absolute);
        state.use(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
    }

    @GameTest(template = TEMPLATE)
    public static void overclockTradesEfficiencyForSpeed(GameTestHelper helper) {
        GeneratorKind.Fuel lava = new GeneratorKind.Fuel(200, 2_500, ItemStack.EMPTY);
        check(Overclock.apply(lava, 0) == lava, "No upgrades must leave the fuel alone");
        GeneratorKind.Fuel previous = lava;
        for (int n = 1; n <= Overclock.MAX; n++) {
            GeneratorKind.Fuel fuel = Overclock.apply(lava, n);
            check(fuel.perTick() == 200 * (1 + n), n + " upgrades: rate " + fuel.perTick());
            check(fuel.perTick() > previous.perTick() && fuel.total() < previous.total(),
                    n + " upgrades must burn faster and give less: " + fuel.total() + " vs " + previous.total());
            check(fuel.total() <= lava.total() * (10 - n) / 10, n + " upgrades: total " + fuel.total());
            previous = fuel;
        }
        GeneratorKind.Fuel max = Overclock.apply(lava, Overclock.MAX);
        check(max.perTick() == 1_000 && max.ticks() == 300, "Four upgrades: " + max);
        check(Overclock.apply(lava, 99).equals(max), "More than the maximum must count as the maximum");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void upgradesInstallRemoveAndDrop(GameTestHelper helper) {
        GeneratorBlockEntity generator = generator(helper, GeneratorKind.FURNACE, POS);
        ServerPlayer player = connectedPlayer(helper, Owner.random("gwOc"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModPower.OVERCLOCK_UPGRADE.get(), 3));
        use(helper, player, POS);
        check(generator.overclock() == 3 && player.getMainHandItem().isEmpty(), "Installed " + generator.overclock());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModPower.OVERCLOCK_UPGRADE.get(), 3));
        use(helper, player, POS);
        check(generator.overclock() == Overclock.MAX && player.getMainHandItem().getCount() == 2,
                "Only " + Overclock.MAX + " fit: installed " + generator.overclock() + ", left " + player.getMainHandItem());
        CompoundTag saved = generator.saveWithoutMetadata();
        check(saved.getInt("Overclock") == Overclock.MAX, "Upgrades are not saved: " + saved);

        generator.setItem(0, new ItemStack(Items.COAL));
        helper.startSequence()
                .thenWaitUntil(() -> check(generator.rate() == 40 * (1 + Overclock.MAX), "Overclocked rate " + generator.rate()))
                .thenExecute(() -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.getInventory().clearContent();
                    player.setShiftKeyDown(true);
                    use(helper, player, POS);
                    check(generator.overclock() == 0, "Sneak-use should remove the upgrades: " + generator.overclock());
                    check(player.getInventory().countItem(ModPower.OVERCLOCK_UPGRADE.get()) == Overclock.MAX,
                            "Removed upgrades were not returned");
                    player.setShiftKeyDown(false);
                    generator.installOverclock(2);
                    helper.destroyBlock(POS);
                })
                .thenExecuteAfter(1, () -> helper.assertItemEntityCountIs(ModPower.OVERCLOCK_UPGRADE.get(), POS, 2.0, 2))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void lavaGeneratorBurnsPipedLava(GameTestHelper helper) {
        GeneratorBlockEntity generator = generator(helper, GeneratorKind.LAVA, POS);
        long bucket = Platform.bucketVolume();
        TestPlatform.FluidAccess tank = platform().fluids(generator, Direction.NORTH);
        check(tank.fill(Fluids.WATER, bucket, false) == 0, "A lava generator accepted water");
        check(tank.fill(Fluids.LAVA, 2 * bucket, false) == 2 * bucket, "Lava was refused");
        check(tank.fill(Fluids.LAVA, 10 * bucket, true) == (GeneratorBlockEntity.LAVA_BUCKETS - 2) * bucket, "Tank capacity");
        check(tank.drain(bucket, false) == 0 && generator.lavaTank().amount() == 2 * bucket, "Piped lava was drained back out");
        check(generator.saveWithoutMetadata().contains("Lava"), "Piped lava is not saved");
        GeneratorBlockEntity survivalist = generator(helper, GeneratorKind.SURVIVALIST, new BlockPos(5, 1, 5));
        check(survivalist.insertLava(Fluids.LAVA, null, bucket, false) == 0, "Only the lava generator takes lava");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    check(generator.rate() == 200, "Burning piped lava: rate " + generator.rate());
                    check(generator.lavaTank().amount() == bucket, "One bucket should be in use, tank " + generator.lavaTank().amount());
                    check(generator.getItem(1).isEmpty(), "Piped lava leaves no bucket behind");
                })
                .thenSucceed();
    }

    /**
     * Fluid transfer nodes (phase 2a) fill tanks through {@link TransferPlatform}, which reaches the
     * same loader API the lava generator exposes; millibuckets on both loaders.
     */
    @GameTest(template = TEMPLATE)
    public static void transferNodesCanFeedTheLavaGenerator(GameTestHelper helper) {
        GeneratorBlockEntity generator = generator(helper, GeneratorKind.LAVA, POS);
        BlockPos pos = helper.absolutePos(POS);
        check(TransferPlatform.hasFluids(helper.getLevel(), pos, Direction.WEST), "Transfer nodes do not see the lava tank");
        check(TransferPlatform.insertFluid(helper.getLevel(), pos, Direction.WEST, Fluids.WATER, 1000, false) == 0,
                "Transfer nodes could pipe water into a lava generator");
        check(TransferPlatform.insertFluid(helper.getLevel(), pos, Direction.WEST, Fluids.LAVA, 1500, false) == 1500,
                "Transfer nodes could not pipe lava in");
        check(generator.lavaTank().amount() == Platform.bucketVolume() * 3 / 2, "Tank " + generator.lavaTank().amount());
        check(TransferPlatform.extractFluid(helper.getLevel(), pos, Direction.WEST, fluid -> true, 1000, true).isEmpty(),
                "Transfer nodes could pull lava back out");
        helper.succeed();
    }
}
