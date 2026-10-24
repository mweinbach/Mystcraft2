package ports.gridwright.machine;

import ports.gridwright.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The generator family and what each one burns. Every fuel gives a fixed rate in energy units per
 * tick for a fixed number of ticks. The numbers are Gridwright's own, chosen so that a specialised
 * generator beats burning the same item in a plain one.
 */
public enum GeneratorKind {
    /** Any furnace fuel, slowly: 10 per tick for the item's whole burn time. Needs no Grid Power to build. */
    SURVIVALIST,
    /** Any furnace fuel, quickly: 40 per tick for half the burn time (twice the survivalist's total). */
    FURNACE,
    /** Food: 40 per tick, 800 units per hunger point scaled up by saturation. */
    CULINARY,
    /** Lava buckets: 200 per tick for 2,500 ticks, returning the bucket. */
    LAVA,
    /** Brewed potions: the more brewing steps, the higher the rate, for 400 ticks. */
    POTION,
    /** Ender pearls and eyes of ender: 80 or 160 per tick for 400 ticks. */
    ENDER,
    /** Redstone dust (40 per tick for 200 ticks) or blocks (for 1,800 ticks). */
    REDSTONE,
    // Phase 3: the rest of the family. Every rainbow generator needs one of each running.
    /** Gunpowder (80 per tick for 200 ticks) and TNT (160 per tick for 600 ticks). */
    TNT,
    /** Anything in {@code #gridwright:pink_generator_fuels}: 60 per tick, 400 ticks (blocks 1,200). */
    PINK,
    /** Nether stars: 500 per tick for 24,000 ticks (20 minutes). */
    NETHER_STAR,
    /** What monsters leave behind: flesh, bones, eyes, string; heads burn longest. */
    DEATH,
    /** Enchanted books and gear: 40 per tick per enchantment level; the enchantments burn off, the item stays. */
    ENCHANT,
    /** Slimeballs and magma cream, and slime blocks for nine times as long. */
    SLIME,
    /** Snow and ice; packed and blue ice burn as the ice that made them. */
    FROSTY,
    /** Dragon's breath: 400 per tick for 600 ticks, returning the bottle. */
    HALITOSIS;

    public static final long CAPACITY = 200_000;
    /** The most a generator pushes into its neighbours, or lets one neighbour take, per tick. */
    public static final long MAX_OUTPUT = 2_000;
    /** Items the pink generator burns; packs add their own pink things. */
    public static final TagKey<Item> PINK_FUELS = TagKey.create(Registry.ITEM_REGISTRY,
            new ResourceLocation("gridwright", "pink_generator_fuels"));
    /** Blocks of pink things burn this many times as long as the dye. */
    private static final int PINK_BLOCK = 3;
    /** The enchant generator's rate per enchantment level, and the most it burns at. */
    public static final int ENCHANT_PER_LEVEL = 40;
    public static final int ENCHANT_MAX_RATE = 1_000;

    /** One burn: {@code perTick} units for {@code ticks} ticks, leaving {@code remainder} behind. */
    public record Fuel(int perTick, int ticks, ItemStack remainder) {
        public long total() {
            return (long) perTick * ticks;
        }
    }

    /** What burning one of {@code stack} gives, or {@code null} if this generator cannot burn it. */
    @Nullable
    public Fuel fuel(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return switch (this) {
            case SURVIVALIST -> furnaceFuel(stack, 10, 1);
            case FURNACE -> furnaceFuel(stack, 40, 2);
            case CULINARY -> food(stack);
            case LAVA -> stack.is(Items.LAVA_BUCKET) ? new Fuel(200, 2_500, new ItemStack(Items.BUCKET)) : null;
            case POTION -> potion(stack);
            case ENDER -> stack.is(Items.ENDER_PEARL) ? new Fuel(80, 400, ItemStack.EMPTY)
                    : stack.is(Items.ENDER_EYE) ? new Fuel(160, 400, ItemStack.EMPTY) : null;
            case REDSTONE -> stack.is(Items.REDSTONE) ? new Fuel(40, 200, ItemStack.EMPTY)
                    : stack.is(Items.REDSTONE_BLOCK) ? new Fuel(40, 1_800, ItemStack.EMPTY) : null;
            case TNT -> stack.is(Items.GUNPOWDER) ? new Fuel(80, 200, ItemStack.EMPTY)
                    : stack.is(Items.TNT) ? new Fuel(160, 600, ItemStack.EMPTY) : null;
            case PINK -> stack.is(PINK_FUELS)
                    ? new Fuel(60, stack.getItem() instanceof BlockItem ? 400 * PINK_BLOCK : 400, ItemStack.EMPTY) : null;
            case NETHER_STAR -> stack.is(Items.NETHER_STAR) ? new Fuel(500, 24_000, ItemStack.EMPTY) : null;
            case DEATH -> death(stack);
            case ENCHANT -> enchanted(stack);
            case SLIME -> stack.is(Items.SLIME_BALL) ? new Fuel(40, 400, ItemStack.EMPTY)
                    : stack.is(Items.SLIME_BLOCK) ? new Fuel(40, 3_600, ItemStack.EMPTY)
                    : stack.is(Items.MAGMA_CREAM) ? new Fuel(80, 400, ItemStack.EMPTY) : null;
            case FROSTY -> frosty(stack);
            case HALITOSIS -> stack.is(Items.DRAGON_BREATH) ? new Fuel(400, 600, new ItemStack(Items.GLASS_BOTTLE)) : null;
        };
    }

    @Nullable
    private static Fuel furnaceFuel(ItemStack stack, int perTick, int divisor) {
        int burn = Platform.burnTime(stack) / divisor;
        if (burn <= 0) return null;
        return new Fuel(perTick, burn, stack.is(Items.LAVA_BUCKET) ? new ItemStack(Items.BUCKET) : ItemStack.EMPTY);
    }

    @Nullable
    private static Fuel food(ItemStack stack) {
        FoodProperties food = stack.getItem().getFoodProperties();
        if (food == null || food.getNutrition() <= 0) return null;
        long total = Math.round(800.0 * food.getNutrition() * (1.0 + 2.0 * Math.max(0, food.getSaturationModifier())));
        int ticks = (int) Math.max(1, total / 40);
        ItemStack remainder = stack.is(Items.MUSHROOM_STEW) || stack.is(Items.RABBIT_STEW) || stack.is(Items.BEETROOT_SOUP)
                || stack.is(Items.SUSPICIOUS_STEW) ? new ItemStack(Items.BOWL)
                : stack.is(Items.HONEY_BOTTLE) ? new ItemStack(Items.GLASS_BOTTLE) : ItemStack.EMPTY;
        return new Fuel(40, ticks, remainder);
    }

    @Nullable
    private static Fuel potion(ItemStack stack) {
        boolean drinkable = stack.is(Items.POTION);
        if (!drinkable && !stack.is(Items.SPLASH_POTION) && !stack.is(Items.LINGERING_POTION)) return null;
        int steps = brewingSteps(PotionUtils.getPotion(stack));
        if (steps <= 0) return null;
        if (stack.is(Items.SPLASH_POTION)) steps += 1;
        if (stack.is(Items.LINGERING_POTION)) steps += 2;
        return new Fuel(20 << Math.min(steps, 6), 400, drinkable ? new ItemStack(Items.GLASS_BOTTLE) : ItemStack.EMPTY);
    }

    @Nullable
    private static Fuel death(ItemStack stack) {
        if (stack.is(Items.ROTTEN_FLESH) || stack.is(Items.BONE) || stack.is(Items.STRING)) return new Fuel(40, 200, ItemStack.EMPTY);
        if (stack.is(Items.SPIDER_EYE) || stack.is(Items.PHANTOM_MEMBRANE)) return new Fuel(60, 300, ItemStack.EMPTY);
        if (stack.is(Items.BONE_BLOCK)) return new Fuel(40, 1_800, ItemStack.EMPTY);
        if (stack.is(Items.ZOMBIE_HEAD) || stack.is(Items.SKELETON_SKULL) || stack.is(Items.CREEPER_HEAD)) {
            return new Fuel(200, 1_200, ItemStack.EMPTY);
        }
        if (stack.is(Items.WITHER_SKELETON_SKULL)) return new Fuel(400, 2_400, ItemStack.EMPTY);
        return null;
    }

    @Nullable
    private static Fuel frosty(ItemStack stack) {
        if (stack.is(Items.SNOWBALL)) return new Fuel(20, 50, ItemStack.EMPTY);
        if (stack.is(Items.SNOW_BLOCK)) return new Fuel(20, 200, ItemStack.EMPTY);
        if (stack.is(Items.POWDER_SNOW_BUCKET)) return new Fuel(40, 400, new ItemStack(Items.BUCKET));
        if (stack.is(Items.ICE)) return new Fuel(40, 200, ItemStack.EMPTY);
        if (stack.is(Items.PACKED_ICE)) return new Fuel(40, 1_800, ItemStack.EMPTY);
        if (stack.is(Items.BLUE_ICE)) return new Fuel(40, 16_200, ItemStack.EMPTY);
        return null;
    }

    /**
     * Enchanted books and gear burn their enchantments: {@link #ENCHANT_PER_LEVEL} per tick per level
     * (curses count for nothing) for 400 ticks. A book leaves a plain book; gear comes back with only its
     * curses, its damage and name unchanged, so nothing is ever destroyed or duplicated.
     */
    @Nullable
    private static Fuel enchanted(ItemStack stack) {
        boolean book = stack.is(Items.ENCHANTED_BOOK);
        // Reads a book's stored enchantments as well as gear's own.
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
        int levels = enchantments.entrySet().stream().filter(e -> !e.getKey().isCurse()).mapToInt(Map.Entry::getValue).sum();
        if (levels <= 0) return null;
        ItemStack remainder;
        if (book) {
            remainder = new ItemStack(Items.BOOK);
        } else {
            remainder = stack.copy();
            remainder.setCount(1);
            remainder.removeTagKey("Enchantments");
            Map<Enchantment, Integer> curses = enchantments.entrySet().stream().filter(e -> e.getKey().isCurse())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            if (!curses.isEmpty()) EnchantmentHelper.setEnchantments(curses, remainder);
        }
        return new Fuel(Math.min(ENCHANT_MAX_RATE, ENCHANT_PER_LEVEL * levels), 400, remainder);
    }

    /**
     * Brewing steps from a water bottle: 1 for awkward, mundane and thick potions, 2 for a potion with
     * an effect, 3 for its long or strong form. Water bottles and uncraftable potions do not burn.
     */
    public static int brewingSteps(Potion potion) {
        if (potion == Potions.EMPTY || potion == Potions.WATER) return 0;
        if (potion.getEffects().isEmpty()) return 1;
        String name = Registry.POTION.getKey(potion).getPath();
        return name.startsWith("long_") || name.startsWith("strong_") ? 3 : 2;
    }
}
