package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import ports.gridwright.machine.GeneratorBlockEntity;
import ports.gridwright.machine.GeneratorKind;
import ports.gridwright.power.RainbowGeneratorBlockEntity;
import ports.gridwright.power.RainbowGeneratorBlockEntity.Status;
import ports.gridwright.registry.ModPower;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static ports.gridwright.gametest.GameTestSupport.Owner;
import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.generator;
import static ports.gridwright.gametest.GameTestSupport.owned;
import static ports.gridwright.gametest.GameTestSupport.place;
import static ports.gridwright.gametest.GameTestSupport.snapshot;

/**
 * The rainbow generator. Each test runs in its own batch: a rainbow generator looks eight blocks out
 * for others, past its own arena, and GameTests in one batch share a level.
 */
public class RainbowGameTests {

    private static final BlockPos RAINBOW = new BlockPos(4, 3, 4);

    private static RainbowGeneratorBlockEntity rainbow(GameTestHelper helper, BlockPos pos, Owner owner) {
        return owned(place(helper, ModPower.RAINBOW_GENERATOR, pos), owner);
    }

    static ItemStack fuel(GeneratorKind kind) {
        return switch (kind) {
            case SURVIVALIST, FURNACE -> new ItemStack(Items.COAL, 8);
            case CULINARY -> new ItemStack(Items.BREAD, 8);
            case LAVA -> new ItemStack(Items.LAVA_BUCKET);
            case POTION -> PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.AWKWARD);
            case ENDER -> new ItemStack(Items.ENDER_PEARL, 8);
            case REDSTONE -> new ItemStack(Items.REDSTONE_BLOCK, 2);
            case TNT -> new ItemStack(Items.TNT, 4);
            case PINK -> new ItemStack(Items.PINK_WOOL, 4);
            case NETHER_STAR -> new ItemStack(Items.NETHER_STAR);
            case DEATH -> new ItemStack(Items.BONE_BLOCK, 2);
            case ENCHANT -> EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.SHARPNESS, 2));
            case SLIME -> new ItemStack(Items.SLIME_BLOCK);
            case FROSTY -> new ItemStack(Items.PACKED_ICE, 2);
            case HALITOSIS -> new ItemStack(Items.DRAGON_BREATH, 4);
        };
    }

    /** Fifteen generators fit in two rows of seven and a spare, all within range of {@link #RAINBOW}. */
    static BlockPos slot(GeneratorKind kind) {
        int index = kind.ordinal();
        return new BlockPos(1 + index % 7, 3, index < 7 ? 1 : index < 14 ? 2 : 6);
    }

    /** One fuelled generator of every kind, all within range of the rainbow generator. */
    private static Map<GeneratorKind, GeneratorBlockEntity> generators(GameTestHelper helper) {
        Map<GeneratorKind, GeneratorBlockEntity> generators = new EnumMap<>(GeneratorKind.class);
        for (GeneratorKind kind : GeneratorKind.values()) {
            GeneratorBlockEntity generator = generator(helper, kind, slot(kind));
            generator.setItem(0, fuel(kind));
            generators.put(kind, generator);
        }
        return generators;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300, batch = "gridwright_rainbow_running")
    public static void rainbowNeedsEveryGeneratorRunning(GameTestHelper helper) {
        Owner owner = Owner.random("gwRb");
        RainbowGeneratorBlockEntity rainbow = rainbow(helper, RAINBOW, owner);
        Map<GeneratorKind, GeneratorBlockEntity> generators = generators(helper);
        BlockPos ender = slot(GeneratorKind.ENDER);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    check(rainbow.status() == Status.RUNNING, "Status " + rainbow.status() + ", missing " + rainbow.missing());
                    check(rainbow.output() == RainbowGeneratorBlockEntity.OUTPUT, "Output " + rainbow.output());
                    check(snapshot(helper, owner.gridKey()).capacity() == RainbowGeneratorBlockEntity.OUTPUT, "Grid capacity");
                })
                .thenExecute(() -> helper.destroyBlock(ender))
                .thenWaitUntil(() -> {
                    check(rainbow.status() == Status.MISSING, "Status with no ender generator: " + rainbow.status());
                    check(rainbow.missing().equals(Set.of(GeneratorKind.ENDER)), "Missing " + rainbow.missing());
                    check(rainbow.output() == 0, "Output without an ender generator " + rainbow.output());
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300, batch = "gridwright_rainbow_idle")
    public static void idleGeneratorsDoNotCount(GameTestHelper helper) {
        Owner owner = Owner.random("gwRi");
        RainbowGeneratorBlockEntity rainbow = rainbow(helper, RAINBOW, owner);
        Map<GeneratorKind, GeneratorBlockEntity> generators = generators(helper);
        generators.get(GeneratorKind.REDSTONE).setItem(0, ItemStack.EMPTY);
        helper.startSequence()
                .thenIdle(60)
                .thenExecuteFor(20, () -> {
                    check(rainbow.status() == Status.MISSING && rainbow.missing().equals(Set.of(GeneratorKind.REDSTONE)),
                            "An unfuelled redstone generator counted: " + rainbow.status() + " " + rainbow.missing());
                    check(rainbow.output() == 0, "Output " + rainbow.output());
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300, batch = "gridwright_rainbow_crowded")
    public static void neighbouringRainbowsBothStop(GameTestHelper helper) {
        Owner owner = Owner.random("gwRc");
        RainbowGeneratorBlockEntity first = rainbow(helper, RAINBOW, owner);
        RainbowGeneratorBlockEntity second = rainbow(helper, new BlockPos(4, 3, 7), owner);
        generators(helper);
        helper.startSequence()
                .thenIdle(45)
                .thenExecuteFor(20, () -> {
                    check(first.status() == Status.CROWDED && second.status() == Status.CROWDED,
                            "Overlapping rainbow generators: " + first.status() + ", " + second.status());
                    check(first.output() == 0 && second.output() == 0, "Crowded rainbow generators made GP");
                })
                .thenSucceed();
    }
}
