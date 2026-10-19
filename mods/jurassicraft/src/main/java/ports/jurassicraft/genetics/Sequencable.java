package ports.jurassicraft.genetics;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Implement on an item or its block to supply a sequencer result. On success the
 * machine consumes one input and one blank storage disc and stores the returned
 * stack in that lane's output slot. Methods receive copies and must be free of
 * side effects; they may also be called for previews.
 */
public interface Sequencable {
    /** Whether this particular stack can be sequenced. */
    default boolean isSequencable(ItemStack input) { return true; }

    /** Return the output, or {@link ItemStack#EMPTY} if this sample produces nothing. */
    ItemStack sequence(ItemStack input, RandomSource random);
}
