package ports.gridwright.machine;

import ports.gridwright.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;

import javax.annotation.Nullable;

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
    REDSTONE;

    public static final long CAPACITY = 200_000;
    /** The most a generator pushes into its neighbours, or lets one neighbour take, per tick. */
    public static final long MAX_OUTPUT = 2_000;

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
