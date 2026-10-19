package ports.jurassicraft.bugs;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.supplies.DinosaurSupplies;

/** Number of insects bred by one food item, matching the original food table. */
public final class BugFoods {
    private BugFoods() {}
    public static boolean isBug(ItemStack stack) {
        return stack.is(BugRegistry.CRICKETS.get()) || stack.is(BugRegistry.COCKROACHES.get()) || stack.is(BugRegistry.MEALWORM_BEETLES.get());
    }
    public static int offspring(ItemStack bug,ItemStack food) {
        if (food.isEmpty()) return 0;
        if (bug.is(BugRegistry.CRICKETS.get())) {
            if (food.is(Items.WHEAT_SEEDS)) return 1;
            if (food.is(Items.GRASS) || food.is(Items.FERN)) return 2;
            if (food.is(Items.WHEAT)) return 3;
            if (food.is(ItemTags.LEAVES)) return 7;
            if (food.is(Items.HAY_BLOCK)) return 27;
        } else if (bug.is(BugRegistry.COCKROACHES.get())) {
            if (food.is(Items.WHEAT_SEEDS) || food.is(Items.MELON_SEEDS)) return 1;
            if (food.is(Items.WHEAT) || food.is(Items.PUMPKIN_SEEDS)) return 2;
            if (food.is(Items.MELON_SLICE) || food.is(Items.POTATO)) return 3;
            if (food.is(Items.CARROT)) return 4;
            if (food.is(Items.BREAD) || food.is(Items.COD) || food.is(Items.SALMON) || food.is(Items.TROPICAL_FISH) || food.is(Items.PUFFERFISH)) return 6;
            if (food.is(Items.CHICKEN) || food.is(Items.COOKED_CHICKEN)) return 7;
            if (food.is(Items.PORKCHOP) || food.is(Items.COOKED_PORKCHOP)) return 8;
            if (food.is(Items.BEEF) || food.is(Items.COOKED_BEEF)) return 10;
            if (isDinosaurMeat(food)) return 12;
            if (food.is(Items.HAY_BLOCK) || food.is(Items.PUMPKIN)) return 16;
            if (food.is(Items.MELON)) return 27;
        } else if (bug.is(BugRegistry.MEALWORM_BEETLES.get())) {
            if (food.is(Items.WHEAT_SEEDS) || food.is(Items.MELON_SEEDS)) return 1;
            if (food.is(Items.WHEAT) || food.is(Items.PUMPKIN_SEEDS)) return 2;
            if (food.is(Items.POTATO)) return 3;
            if (food.is(Items.CARROT)) return 4;
            if (food.is(Items.BREAD)) return 6;
            if (food.is(Items.HAY_BLOCK)) return 16;
        }
        return 0;
    }
    private static boolean isDinosaurMeat(ItemStack food) {
        if (food.is(DinosaurRegistry.DINOSAUR_MEAT.get()) || food.is(DinosaurSupplies.STEAK.get())) return true;
        for (Species species : Species.values()) if (food.is(DinosaurRegistry.rawMeat(species).get()) || food.is(DinosaurRegistry.cookedMeat(species).get())) return true;
        return false;
    }
    public static boolean isFood(ItemStack stack) {
        return offspring(new ItemStack(BugRegistry.CRICKETS.get()),stack)>0 || offspring(new ItemStack(BugRegistry.COCKROACHES.get()),stack)>0 || offspring(new ItemStack(BugRegistry.MEALWORM_BEETLES.get()),stack)>0;
    }
}
