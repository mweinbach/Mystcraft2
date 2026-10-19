package ports.jurassicraft.cleaning;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Implement on an item or its block to allow it to be cleaned in the station. */
public interface Cleanable {
    default boolean isCleanable(ItemStack input) { return true; }
    ItemStack clean(ItemStack input, RandomSource random);
}
