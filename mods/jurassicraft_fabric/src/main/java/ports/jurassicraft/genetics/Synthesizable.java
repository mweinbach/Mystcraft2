package ports.jurassicraft.genetics;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Implement on an item or its block to supply a DNA synthesizer result. Inputs
 * must carry a {@code DNAQuality} tag, as in the original machine. On success the
 * machine retains the input, consumes one empty test tube and one DNA base
 * material, and stores the returned stack. Methods receive copies and must be
 * free of side effects; the machine may ask for results when checking output space.
 */
public interface Synthesizable {
    /** Whether this particular stack can be synthesized. */
    default boolean isSynthesizable(ItemStack input) { return true; }

    /** Return the output, or {@link ItemStack#EMPTY} if this sample produces nothing. */
    ItemStack synthesize(ItemStack input, RandomSource random);
}
