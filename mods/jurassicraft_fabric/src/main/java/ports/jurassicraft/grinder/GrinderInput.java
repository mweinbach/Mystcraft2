package ports.jurassicraft.grinder;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Extension point for future grindable items such as fossilized eggs. */
public interface GrinderInput {
    /** Return an empty stack if the input cannot currently be processed. */
    ItemStack grind(ItemStack input, RandomSource random);
}
