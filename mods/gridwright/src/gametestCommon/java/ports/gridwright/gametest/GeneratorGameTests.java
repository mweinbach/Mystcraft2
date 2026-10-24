package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.gridwright.machine.GeneratorBlockEntity;
import ports.gridwright.machine.GeneratorKind;

import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.generator;
import static ports.gridwright.gametest.GameTestSupport.platform;

/**
 * Every generator: what it burns and for how long, what it leaves behind, and that its energy reaches
 * other mods through this loader's energy API.
 */
public class GeneratorGameTests {

    private static final BlockPos POS = new BlockPos(2, 2, 2);

    static void fuel(GeneratorKind kind, ItemStack stack, int perTick, int ticks) {
        GeneratorKind.Fuel fuel = kind.fuel(stack);
        check(fuel != null, kind + " should burn " + stack);
        check(fuel.perTick() == perTick && fuel.ticks() == ticks,
                kind + " burning " + stack + ": expected " + perTick + "/t for " + ticks + ", got " + fuel);
    }

    static void rejects(GeneratorKind kind, ItemStack stack) {
        check(kind.fuel(stack) == null, kind + " should not burn " + stack);
    }

    private static ItemStack potion(net.minecraft.world.item.Item item, net.minecraft.world.item.alchemy.Potion potion) {
        return PotionUtils.setPotion(new ItemStack(item), potion);
    }

    @GameTest(template = TEMPLATE)
    public static void fuelTable(GameTestHelper helper) {
        fuel(GeneratorKind.SURVIVALIST, new ItemStack(Items.COAL), 10, 1_600);
        fuel(GeneratorKind.SURVIVALIST, new ItemStack(Items.OAK_PLANKS), 10, 300);
        rejects(GeneratorKind.SURVIVALIST, new ItemStack(Items.DIRT));
        fuel(GeneratorKind.FURNACE, new ItemStack(Items.COAL), 40, 800);
        rejects(GeneratorKind.FURNACE, new ItemStack(Items.REDSTONE));
        fuel(GeneratorKind.CULINARY, new ItemStack(Items.BREAD), 40, 220);
        fuel(GeneratorKind.CULINARY, new ItemStack(Items.COOKED_BEEF), 40, 416);
        rejects(GeneratorKind.CULINARY, new ItemStack(Items.COAL));
        fuel(GeneratorKind.LAVA, new ItemStack(Items.LAVA_BUCKET), 200, 2_500);
        rejects(GeneratorKind.LAVA, new ItemStack(Items.BUCKET));
        fuel(GeneratorKind.POTION, potion(Items.POTION, Potions.AWKWARD), 40, 400);
        fuel(GeneratorKind.POTION, potion(Items.POTION, Potions.SWIFTNESS), 80, 400);
        fuel(GeneratorKind.POTION, potion(Items.POTION, Potions.LONG_SWIFTNESS), 160, 400);
        fuel(GeneratorKind.POTION, potion(Items.SPLASH_POTION, Potions.SWIFTNESS), 160, 400);
        fuel(GeneratorKind.POTION, potion(Items.LINGERING_POTION, Potions.STRONG_SWIFTNESS), 640, 400);
        rejects(GeneratorKind.POTION, potion(Items.POTION, Potions.WATER));
        fuel(GeneratorKind.ENDER, new ItemStack(Items.ENDER_PEARL), 80, 400);
        fuel(GeneratorKind.ENDER, new ItemStack(Items.ENDER_EYE), 160, 400);
        rejects(GeneratorKind.ENDER, new ItemStack(Items.ENDER_CHEST));
        fuel(GeneratorKind.REDSTONE, new ItemStack(Items.REDSTONE), 40, 200);
        fuel(GeneratorKind.REDSTONE, new ItemStack(Items.REDSTONE_BLOCK), 40, 1_800);
        rejects(GeneratorKind.REDSTONE, new ItemStack(Items.GLOWSTONE_DUST));
        // A specialised generator always beats burning the same item in the survivalist generator.
        check(GeneratorKind.FURNACE.fuel(new ItemStack(Items.COAL)).total() == 2 * GeneratorKind.SURVIVALIST.fuel(new ItemStack(Items.COAL)).total(),
                "Furnace generator should double the survivalist's total");
        helper.succeed();
    }

    /**
     * Inserts {@code fuel} through the loader's item API and checks the generator banks exactly its rate
     * for every tick it has burned, and that the energy can be taken through the loader's energy API.
     */
    static void burns(GameTestHelper helper, GeneratorKind kind, ItemStack fuel, int perTick, ItemStack remainder) {
        GeneratorBlockEntity generator = generator(helper, kind, POS);
        TestPlatform.ItemAccess items = platform().items(generator, Direction.UP);
        check(!items.insert(new ItemStack(Items.DIRT)).isEmpty(), kind + " accepted dirt");
        ItemStack left = items.insert(fuel.copy());
        check(left.isEmpty(), kind + " refused its own fuel " + fuel);
        int ticks = kind.fuel(fuel).ticks();
        TestPlatform.EnergyAccess energy = platform().energy(generator, Direction.NORTH);
        check(energy.capacity() == GeneratorKind.CAPACITY, "Capacity " + energy.capacity());
        check(energy.insert(100, true) == 0, "Generators must not accept energy");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    int burned = ticks - generator.burnLeft();
                    check(generator.rate() == perTick, kind + " rate " + generator.rate());
                    check(burned >= 10 && energy.stored() >= 150, kind + " has only burned " + burned + " ticks");
                    check(energy.stored() == (long) perTick * burned, kind + " stored " + energy.stored() + " after "
                            + burned + " ticks at " + perTick);
                    check(ItemStack.matches(generator.getItem(1), remainder), kind + " left " + generator.getItem(1));
                })
                .thenExecute(() -> {
                    long before = energy.stored();
                    long taken = energy.extract(150, false);
                    check(taken == 150, kind + " gave " + taken + " of 150");
                    check(energy.stored() <= before - 150 + perTick, "Extraction not applied");
                    if (!remainder.isEmpty()) {
                        ItemStack out = items.extract(1, 64, false);
                        check(ItemStack.matches(out, remainder), kind + " remainder not extractable: " + out);
                    }
                    check(items.extract(0, 64, true).isEmpty(), kind + " let fuel be pulled back out");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void survivalistBurnsFurnaceFuel(GameTestHelper helper) {
        burns(helper, GeneratorKind.SURVIVALIST, new ItemStack(Items.COAL), 10, ItemStack.EMPTY);
    }

    @GameTest(template = TEMPLATE)
    public static void furnaceGeneratorBurnsFast(GameTestHelper helper) {
        burns(helper, GeneratorKind.FURNACE, new ItemStack(Items.CHARCOAL), 40, ItemStack.EMPTY);
    }

    @GameTest(template = TEMPLATE)
    public static void culinaryBurnsFood(GameTestHelper helper) {
        burns(helper, GeneratorKind.CULINARY, new ItemStack(Items.MUSHROOM_STEW), 40, new ItemStack(Items.BOWL));
    }

    @GameTest(template = TEMPLATE)
    public static void lavaGeneratorReturnsBuckets(GameTestHelper helper) {
        burns(helper, GeneratorKind.LAVA, new ItemStack(Items.LAVA_BUCKET), 200, new ItemStack(Items.BUCKET));
    }

    @GameTest(template = TEMPLATE)
    public static void potionGeneratorReturnsBottles(GameTestHelper helper) {
        burns(helper, GeneratorKind.POTION, potion(Items.POTION, Potions.SWIFTNESS), 80, new ItemStack(Items.GLASS_BOTTLE));
    }

    @GameTest(template = TEMPLATE)
    public static void enderGeneratorBurnsPearls(GameTestHelper helper) {
        burns(helper, GeneratorKind.ENDER, new ItemStack(Items.ENDER_PEARL), 80, ItemStack.EMPTY);
    }

    @GameTest(template = TEMPLATE)
    public static void redstoneGeneratorBurnsDust(GameTestHelper helper) {
        burns(helper, GeneratorKind.REDSTONE, new ItemStack(Items.REDSTONE), 40, ItemStack.EMPTY);
    }

    @GameTest(template = TEMPLATE)
    public static void generatorPushesIntoNeighbours(GameTestHelper helper) {
        GeneratorBlockEntity generator = generator(helper, GeneratorKind.REDSTONE, POS);
        helper.setBlock(POS.east(), Blocks.BARREL);
        BlockEntity sink = helper.getBlockEntity(POS.east());
        generator.setItem(0, new ItemStack(Items.REDSTONE_BLOCK));
        helper.succeedWhen(() -> {
            int burned = 1_800 - generator.burnLeft();
            long sunk = platform().sunk(sink);
            check(burned > 20 && sunk > 0, "Nothing pushed yet: burned " + burned + ", sunk " + sunk);
            check(sunk + generator.energy().stored() == 40L * burned, "Energy lost or created: sunk " + sunk
                    + " + stored " + generator.energy().stored() + " vs " + 40L * burned);
            check(generator.energy().stored() == 0, "A neighbour taking everything should leave nothing stored");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void fullGeneratorWaits(GameTestHelper helper) {
        GeneratorBlockEntity generator = generator(helper, GeneratorKind.SURVIVALIST, POS);
        CompoundTag full = generator.saveWithoutMetadata();
        full.putLong("Energy", GeneratorKind.CAPACITY);
        generator.load(full);
        generator.setItem(0, new ItemStack(Items.COAL, 2));
        helper.startSequence()
                .thenExecuteFor(20, () -> {
                    check(generator.burnLeft() == 0, "A full generator started burning");
                    check(generator.getItem(0).getCount() == 2, "A full generator used fuel");
                    check(generator.energy().stored() == GeneratorKind.CAPACITY, "Stored " + generator.energy().stored());
                })
                .thenExecute(() -> platform().energy(generator, Direction.DOWN).extract(GeneratorKind.MAX_OUTPUT, false))
                .thenWaitUntil(() -> check(generator.burnLeft() > 0 && generator.getItem(0).getCount() == 1,
                        "Generator did not resume after space opened"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void remainderMustFitBeforeBurning(GameTestHelper helper) {
        GeneratorBlockEntity generator = generator(helper, GeneratorKind.LAVA, POS);
        generator.setItem(1, new ItemStack(Items.COBBLESTONE));
        generator.setItem(0, new ItemStack(Items.LAVA_BUCKET));
        helper.startSequence()
                .thenExecuteFor(10, () -> check(generator.burnLeft() == 0 && generator.getItem(0).is(Items.LAVA_BUCKET),
                        "Lava burned with nowhere to put the bucket"))
                .thenExecute(() -> generator.setItem(1, ItemStack.EMPTY))
                .thenWaitUntil(() -> check(generator.burnLeft() > 0 && generator.getItem(1).is(Items.BUCKET), "Lava did not start"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void generatorStateSurvivesReload(GameTestHelper helper) {
        GeneratorBlockEntity generator = generator(helper, GeneratorKind.ENDER, POS);
        generator.setItem(0, new ItemStack(Items.ENDER_EYE));
        helper.startSequence()
                .thenWaitUntil(() -> check(generator.burnLeft() > 0 && generator.energy().stored() > 0, "Not burning"))
                .thenExecute(() -> {
                    CompoundTag saved = generator.saveWithoutMetadata();
                    GeneratorBlockEntity copy = generator(helper, GeneratorKind.ENDER, POS.east(2));
                    copy.load(saved);
                    check(copy.energy().stored() == generator.energy().stored(), "Energy not saved");
                    check(copy.burnLeft() == generator.burnLeft() && copy.rate() == 160, "Burn not saved");
                })
                .thenSucceed();
    }
}
