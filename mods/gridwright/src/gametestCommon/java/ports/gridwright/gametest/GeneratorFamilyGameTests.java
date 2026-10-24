package ports.gridwright.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import ports.gridwright.machine.GeneratorKind;
import ports.gridwright.registry.ModBlocks;

import java.util.Map;

import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GeneratorGameTests.burns;
import static ports.gridwright.gametest.GeneratorGameTests.fuel;
import static ports.gridwright.gametest.GeneratorGameTests.rejects;

/** The phase 3 generators: what each burns, and the enchant generator's burnt-off enchantments. */
public class GeneratorFamilyGameTests {

    @GameTest(template = TEMPLATE)
    public static void phaseThreeFuelTable(GameTestHelper helper) {
        check(ModBlocks.GENERATORS.size() == 15, "Expected fifteen generator kinds, got " + ModBlocks.GENERATORS.size());
        fuel(GeneratorKind.TNT, new ItemStack(Items.GUNPOWDER), 80, 200);
        fuel(GeneratorKind.TNT, new ItemStack(Items.TNT), 160, 600);
        rejects(GeneratorKind.TNT, new ItemStack(Items.TNT_MINECART));
        fuel(GeneratorKind.PINK, new ItemStack(Items.PINK_DYE), 60, 400);
        fuel(GeneratorKind.PINK, new ItemStack(Items.PINK_WOOL), 60, 1_200);
        rejects(GeneratorKind.PINK, new ItemStack(Items.RED_WOOL));
        rejects(GeneratorKind.PINK, new ItemStack(Items.PINK_SHULKER_BOX));
        fuel(GeneratorKind.NETHER_STAR, new ItemStack(Items.NETHER_STAR), 500, 24_000);
        fuel(GeneratorKind.DEATH, new ItemStack(Items.ROTTEN_FLESH), 40, 200);
        fuel(GeneratorKind.DEATH, new ItemStack(Items.WITHER_SKELETON_SKULL), 400, 2_400);
        rejects(GeneratorKind.DEATH, new ItemStack(Items.BEEF));
        fuel(GeneratorKind.SLIME, new ItemStack(Items.SLIME_BLOCK), 40, 3_600);
        fuel(GeneratorKind.SLIME, new ItemStack(Items.MAGMA_CREAM), 80, 400);
        fuel(GeneratorKind.FROSTY, new ItemStack(Items.BLUE_ICE), 40, 16_200);
        // Packed ice is nine ice and blue ice nine packed ice: each burns as the ice that made it.
        check(GeneratorKind.FROSTY.fuel(new ItemStack(Items.PACKED_ICE)).total() == 9 * GeneratorKind.FROSTY.fuel(new ItemStack(Items.ICE)).total(),
                "Packed ice should burn as nine ice");
        rejects(GeneratorKind.FROSTY, new ItemStack(Items.WATER_BUCKET));
        fuel(GeneratorKind.HALITOSIS, new ItemStack(Items.DRAGON_BREATH), 400, 600);
        rejects(GeneratorKind.ENCHANT, new ItemStack(Items.BOOK));
        rejects(GeneratorKind.ENCHANT, EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.VANISHING_CURSE, 1)));
        fuel(GeneratorKind.ENCHANT, EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.SHARPNESS, 3)), 120, 400);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void enchantGeneratorBurnsOffEnchantmentsOnly(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(Enchantments.SHARPNESS, 5);
        sword.enchant(Enchantments.UNBREAKING, 3);
        sword.enchant(Enchantments.VANISHING_CURSE, 1);
        sword.setDamageValue(100);
        sword.setHoverName(net.minecraft.network.chat.Component.literal("Old Faithful"));
        GeneratorKind.Fuel fuel = GeneratorKind.ENCHANT.fuel(sword);
        check(fuel != null && fuel.perTick() == 8 * GeneratorKind.ENCHANT_PER_LEVEL, "Eight levels should burn at 320/t: " + fuel);
        ItemStack left = fuel.remainder();
        Map<net.minecraft.world.item.enchantment.Enchantment, Integer> kept = EnchantmentHelper.getEnchantments(left);
        check(left.is(Items.DIAMOND_SWORD) && left.getCount() == 1, "The sword itself must come back: " + left);
        check(kept.equals(Map.of(Enchantments.VANISHING_CURSE, 1)), "Only curses should stay: " + kept);
        check(left.getDamageValue() == 100 && left.getHoverName().getString().equals("Old Faithful"), "Damage and name must stay");
        check(EnchantmentHelper.getEnchantments(sword).size() == 3, "Working out the fuel must not change the input");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void enchantGeneratorReturnsBooks(GameTestHelper helper) {
        burns(helper, GeneratorKind.ENCHANT, EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.MENDING, 1)),
                GeneratorKind.ENCHANT_PER_LEVEL, new ItemStack(Items.BOOK));
    }

    @GameTest(template = TEMPLATE)
    public static void halitosisGeneratorReturnsBottles(GameTestHelper helper) {
        burns(helper, GeneratorKind.HALITOSIS, new ItemStack(Items.DRAGON_BREATH), 400, new ItemStack(Items.GLASS_BOTTLE));
    }

    @GameTest(template = TEMPLATE)
    public static void pinkGeneratorReadsItsTag(GameTestHelper helper) {
        burns(helper, GeneratorKind.PINK, new ItemStack(Items.PINK_TULIP), 60, ItemStack.EMPTY);
    }
}
