package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import ports.gridwright.grid.GridSnapshot;
import ports.gridwright.machine.GeneratorBlockEntity;
import ports.gridwright.machine.GeneratorKind;
import ports.gridwright.power.ConverterBlock;
import ports.gridwright.power.ConverterBlockEntity;
import ports.gridwright.power.ConverterMode;
import ports.gridwright.power.MillBlockEntity;
import ports.gridwright.power.MillKind;
import ports.gridwright.registry.ModPower;

import static ports.gridwright.gametest.GameTestSupport.Owner;
import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.generator;
import static ports.gridwright.gametest.GameTestSupport.owned;
import static ports.gridwright.gametest.GameTestSupport.place;
import static ports.gridwright.gametest.GameTestSupport.platform;
import static ports.gridwright.gametest.GameTestSupport.snapshot;

/**
 * The power converter: Grid Power to energy and back always loses energy, so no loop through
 * converters, Gridwright's generators or other mods' machines can run itself.
 */
public class PowerGameTests {

    private static final double EPSILON = 1e-6;

    static ConverterBlockEntity converter(GameTestHelper helper, BlockPos pos, Owner owner, ConverterMode mode) {
        helper.setBlock(pos, ModPower.POWER_CONVERTER.get().defaultBlockState().setValue(ConverterBlock.MODE, mode));
        ConverterBlockEntity converter = (ConverterBlockEntity) helper.getBlockEntity(pos);
        check(converter != null, "No converter at " + pos);
        GameTestTicking.keepTestAreaTicking(helper);
        return owned(converter, owner);
    }

    /** A dragon egg mill (64 GP) with its egg on top. */
    static MillBlockEntity eggMill(GameTestHelper helper, BlockPos pos, Owner owner) {
        MillBlockEntity mill = owned(place(helper, ModPower.MILLS.get(MillKind.DRAGON_EGG), pos), owner);
        helper.setBlock(pos.above(), Blocks.DRAGON_EGG);
        return mill;
    }

    private static void near(double actual, double expected, String what) {
        check(Math.abs(actual - expected) < EPSILON, what + ": expected " + expected + ", got " + actual);
    }

    /**
     * 16 GP drawn by a converter making energy becomes 64 energy per tick; a second converter burning
     * those 64 per tick gives back 4 GP. The second converter reports to another owner's grid so the
     * returned GP can be measured on its own; that grid's demand (a third converter) keeps it burning.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void roundTripLosesThreeQuarters(GameTestHelper helper) {
        Owner source = Owner.random("gwP");
        Owner sink = Owner.random("gwQ");
        eggMill(helper, new BlockPos(1, 1, 1), source);
        ConverterBlockEntity dynamo = converter(helper, new BlockPos(3, 1, 1), source, ConverterMode.TO_ENERGY);
        ConverterBlockEntity motor = converter(helper, new BlockPos(4, 1, 1), sink, ConverterMode.TO_GRID);
        converter(helper, new BlockPos(4, 1, 4), sink, ConverterMode.TO_ENERGY);
        helper.startSequence()
                .thenWaitUntil(() -> check(motor.lastGp() > 0, "The motor has made no GP yet"))
                .thenIdle(5)
                .thenExecuteFor(40, () -> {
                    near(dynamo.lastGp(), ConverterBlockEntity.GP_DRAW, "GP the dynamo draws");
                    check(dynamo.lastEnergy() == ConverterBlockEntity.ENERGY_MADE, "Energy the dynamo makes: " + dynamo.lastEnergy());
                    check(motor.lastEnergy() == ConverterBlockEntity.ENERGY_MADE, "Energy the motor burns: " + motor.lastEnergy());
                    near(motor.lastGp(), ConverterBlockEntity.GP_DRAW / 4, "GP the motor makes");
                    GridSnapshot returned = snapshot(helper, sink.gridKey());
                    near(returned.capacity(), 4.0, "returned capacity");
                    check(returned.capacity() <= dynamo.lastGp() / 4 + EPSILON, "Round trip must lose at least 75%: "
                            + dynamo.lastGp() + " GP in, " + returned.capacity() + " GP back");
                    near(snapshot(helper, source.gridKey()).demand(), ConverterBlockEntity.GP_DRAW, "source demand");
                })
                .thenSucceed();
    }

    /**
     * A converter making energy feeding a converter making GP on the same grid, primed with a full
     * store and no other producer, runs down: total stored energy never rises and reaches zero.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void closedLoopRunsDown(GameTestHelper helper) {
        Owner owner = Owner.random("gwR");
        ConverterBlockEntity dynamo = converter(helper, new BlockPos(2, 1, 2), owner, ConverterMode.TO_ENERGY);
        ConverterBlockEntity motor = converter(helper, new BlockPos(3, 1, 2), owner, ConverterMode.TO_GRID);
        motor.restore(ConverterBlockEntity.CAPACITY);
        long[] last = {dynamo.stored() + motor.stored()};
        boolean[] ranDynamo = {false};
        helper.onEachTick(() -> {
            long total = dynamo.stored() + motor.stored();
            check(total <= last[0], "A converter loop gained energy: " + last[0] + " -> " + total);
            last[0] = total;
            if (dynamo.lastEnergy() > 0) ranDynamo[0] = true;
        });
        helper.succeedWhen(() -> {
            check(ranDynamo[0], "The loop never ran (the primed motor should power the dynamo for a while)");
            check(dynamo.stored() + motor.stored() < ConverterBlockEntity.MAX_BURN,
                    "Loop still holds " + (dynamo.stored() + motor.stored()));
            check(dynamo.lastEnergy() == 0, "The dynamo still makes energy with nothing feeding the grid");
            check(!snapshot(helper, owner.gridKey()).powered(), "The grid should be overloaded once the loop ran down");
        });
    }

    /** Energy from any generator becomes GP only through a converter, at 16 energy per GP. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void generatorEnergyBecomesGpAtSixteenToOne(GameTestHelper helper) {
        Owner owner = Owner.random("gwS");
        GeneratorBlockEntity generator = generator(helper, GeneratorKind.SURVIVALIST, new BlockPos(2, 1, 2));
        generator.setItem(0, new ItemStack(Items.COAL, 4));
        ConverterBlockEntity motor = converter(helper, new BlockPos(3, 1, 2), owner, ConverterMode.TO_GRID);
        converter(helper, new BlockPos(5, 1, 5), owner, ConverterMode.TO_ENERGY);
        helper.startSequence()
                .thenWaitUntil(() -> check(motor.lastGp() > 0, "The motor has made no GP yet"))
                .thenIdle(5)
                .thenExecuteFor(20, () -> {
                    check(generator.rate() == 10, "Survivalist rate " + generator.rate());
                    check(motor.lastEnergy() == 10, "Burned " + motor.lastEnergy());
                    near(motor.lastGp(), 10.0 / ConverterBlockEntity.ENERGY_PER_GP_IN, "GP from 10 energy per tick");
                })
                .thenSucceed();
    }

    /** Each mode exposes one direction only, and generators never accept energy back. */
    @GameTest(template = TEMPLATE)
    public static void convertersAndGeneratorsOnlyMoveEnergyOneWay(GameTestHelper helper) {
        Owner owner = Owner.random("gwT");
        ConverterBlockEntity dynamo = converter(helper, new BlockPos(1, 1, 1), owner, ConverterMode.TO_ENERGY);
        ConverterBlockEntity motor = converter(helper, new BlockPos(5, 1, 5), owner, ConverterMode.TO_GRID);
        TestPlatform.EnergyAccess out = platform().energy(dynamo, Direction.UP);
        TestPlatform.EnergyAccess in = platform().energy(motor, Direction.UP);
        check(out.insert(500, false) == 0 && dynamo.stored() == 0, "A converter making energy accepted energy");
        check(in.insert(500, false) == 500 && motor.stored() == 500, "A converter making GP refused energy: " + motor.stored());
        check(in.extract(100, false) == 0 && motor.stored() == 500, "Energy was pulled back out of a converter making GP");
        check(in.insert(5_000, true) == ConverterBlockEntity.MAX_TRANSFER, "Transfer cap");
        dynamo.restore(300);
        check(out.extract(100, false) == 100 && dynamo.stored() == 200, "A converter making energy refused extraction");
        for (GeneratorKind kind : GeneratorKind.values()) {
            GeneratorBlockEntity generator = generator(helper, kind, new BlockPos(3, 1 + kind.ordinal() % 3, 1 + kind.ordinal() / 3));
            check(platform().energy(generator, Direction.UP).insert(1_000, false) == 0, kind + " generator accepted energy");
        }
        helper.succeed();
    }
}
