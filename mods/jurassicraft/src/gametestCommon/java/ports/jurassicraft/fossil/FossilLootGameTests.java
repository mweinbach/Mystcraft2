package ports.jurassicraft.fossil;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Exercise the actual loaded tables with ordinary, enchanted and Silk Touch tools. */
public final class FossilLootGameTests {
    private static final int ROLLS = 6000;
    private FossilLootGameTests() {}
    private static void check(GameTestHelper h, boolean ok, String message) { if (!ok) h.fail(message); }

    private static LootTable table(GameTestHelper h, String name) {
        return h.getLevel().getServer().getLootTables().get(new ResourceLocation(PortMod.MOD_ID, "blocks/" + name));
    }

    private static LootContext context(GameTestHelper h, Block block, int fortune, boolean silk, long seed) {
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        if (fortune > 0) tool.enchant(Enchantments.BLOCK_FORTUNE, fortune);
        if (silk) tool.enchant(Enchantments.SILK_TOUCH, 1);
        return new LootContext.Builder(h.getLevel()).withRandom(RandomSource.create(seed))
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .withParameter(LootContextParams.BLOCK_STATE, block.defaultBlockState())
                .withParameter(LootContextParams.TOOL, tool).create(LootContextParamSets.BLOCK);
    }

    private static void probability(GameTestHelper h, double actual, double expected, String description) {
        check(h, Math.abs(actual - expected) < .035D, description + ": expected " + expected + ", got " + actual);
    }

    private static double plantCdf(double threshold, int fortune) {
        double bonus = .3D * fortune;
        if (bonus == 0) return threshold;
        if (threshold + bonus <= 1) return threshold + bonus / 2;
        return 1 - (1 - threshold) * (1 - threshold) / (2 * bonus);
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void plantFortuneUsesOriginalLuck(GameTestHelper h) {
        var table = table(h, "plant_fossil_block");
        Item[] items = {JurassicContent.PLANT_FOSSIL.get(), JurassicContent.TWIG_FOSSIL.get(), Items.COAL, Items.FLINT, Items.COBBLESTONE};
        double[] limits = {.2D, .35D, .65D, .8D, 1D};
        for (int fortune : new int[] {0, 1, 2, 3, 4}) {
            var context = context(h, JurassicContent.PLANT_FOSSIL_BLOCK.get(), fortune, false, 171L + fortune);
            int[] counts = new int[items.length];
            for (int i = 0; i < ROLLS; i++) {
                var stacks = table.getRandomItems(context);
                check(h, stacks.size() == 1, "plant fossil must drop one item");
                int index = -1;
                for (int n = 0; n < items.length; n++) if (stacks.get(0).is(items[n])) index = n;
                check(h, index >= 0, "unexpected plant fossil item");
                counts[index]++;
            }
            double previous = 0;
            for (int i = 0; i < items.length; i++) {
                double cumulative = plantCdf(limits[i], fortune);
                probability(h, counts[i] / (double) ROLLS, cumulative - previous, "plant Fortune " + fortune + " item " + i);
                previous = cumulative;
            }
        }
        h.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void amberFortuneKeepsIndependentVariants(GameTestHelper h) {
        var table = table(h, "amber_ore");
        for (int fortune : new int[] {0, 1, 2, 3, 4}) {
            var context = context(h, JurassicContent.AMBER_ORE.get(), fortune, false, 211L + fortune);
            int[] counts = new int[Math.max(1, fortune) + 1];
            int plain = 0, aphid = 0, mixed = 0;
            for (int i = 0; i < ROLLS; i++) {
                var stacks = table.getRandomItems(context);
                boolean hasPlain = false, hasAphid = false;
                int total = 0;
                for (ItemStack stack : stacks) {
                    check(h, stack.is(JurassicContent.AMBER.get()) || stack.is(JurassicContent.AMBER_APHID.get()), "unexpected amber item");
                    total += stack.getCount();
                    if (stack.is(JurassicContent.AMBER.get())) { hasPlain = true; plain += stack.getCount(); }
                    else { hasAphid = true; aphid += stack.getCount(); }
                }
                check(h, total >= 1 && total < counts.length, "wrong amber count at Fortune " + fortune);
                counts[total]++;
                if (hasPlain && hasAphid) mixed++;
            }
            double first = fortune == 0 ? 1D : 2D / (fortune + 1);
            probability(h, counts[1] / (double) ROLLS, first, "amber Fortune " + fortune + " single");
            for (int n = 2; n <= fortune; n++)
                probability(h, counts[n] / (double) ROLLS, 1D / (fortune + 1), "amber Fortune " + fortune + " count " + n);
            probability(h, plain / (double) (plain + aphid), .5D, "amber variant distribution");
            if (fortune >= 2) check(h, mixed > 0, "multiple amber drops must be able to contain both variants");
        }
        h.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void gypsumIceAndSilkPreserveOriginalRules(GameTestHelper h) {
        var gypsum = table(h, "gypsum_stone");
        var ice = table(h, "ice_shard");
        for (int fortune : new int[] {0, 1, 2, 3, 4}) {
            var gypsumContext = context(h, JurassicContent.GYPSUM_STONE.get(), fortune, false, 311L + fortune);
            var iceContext = context(h, JurassicContent.ICE_SHARD.get(), fortune, false, 411L + fortune);
            Map<Integer, Integer> observed = new HashMap<>();
            int lampreys = 0, iceBlocks = 0;
            for (int i = 0; i < ROLLS; i++) {
                var drops = gypsum.getRandomItems(gypsumContext);
                check(h, drops.size() == 1 && drops.get(0).is(JurassicContent.GYPSUM_POWDER.get()), "gypsum must drop powder");
                observed.merge(drops.get(0).getCount(), 1, Integer::sum);
                var iceDrops = ice.getRandomItems(iceContext);
                check(h, iceDrops.size() <= 1, "ice shard must drop at most one item");
                if (!iceDrops.isEmpty()) {
                    ItemStack stack = iceDrops.get(0);
                    check(h, stack.is(JurassicContent.SEA_LAMPREY.get()) || stack.is(Items.ICE), "unexpected ice shard item");
                    if (stack.is(JurassicContent.SEA_LAMPREY.get())) lampreys++; else iceBlocks++;
                }
            }
            Map<Integer, Double> expected = new HashMap<>();
            int outcomes = fortune == 0 ? 1 : fortune + 2;
            for (int roll = 0; roll < outcomes; roll++) for (int base = 4; base <= 8; base++) {
                int multiplier = fortune == 0 ? 1 : Math.max(0, roll - 2) + 1;
                expected.merge(base * multiplier, 1D / (outcomes * 5), Double::sum);
            }
            check(h, expected.keySet().containsAll(observed.keySet()), "gypsum produced an impossible count");
            for (var entry : expected.entrySet())
                probability(h, observed.getOrDefault(entry.getKey(), 0) / (double) ROLLS, entry.getValue(), "gypsum Fortune " + fortune + " count " + entry.getKey());
            double lampreyChance = Math.min(1D, .2D + fortune / 3D * .8D);
            probability(h, lampreys / (double) ROLLS, lampreyChance, "lamprey Fortune " + fortune);
            probability(h, iceBlocks / (double) ROLLS, (1 - lampreyChance) * .2D, "ice Fortune " + fortune);
        }
        for (var entry : Map.of("plant_fossil_block", JurassicContent.PLANT_FOSSIL_BLOCK.get(),
                "amber_ore", JurassicContent.AMBER_ORE.get(), "gypsum_stone", JurassicContent.GYPSUM_STONE.get(),
                "ice_shard", JurassicContent.ICE_SHARD.get()).entrySet()) {
            var drops = table(h, entry.getKey()).getRandomItems(context(h, entry.getValue(), 0, true, 19));
            check(h, drops.size() == 1 && drops.get(0).is(entry.getValue().asItem()), "Silk Touch must drop " + entry.getKey());
        }
        h.succeed();
    }
}
