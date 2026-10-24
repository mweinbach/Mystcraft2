package ports.buildcraft.transport.stripes;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * Upstream {@code IStripesActivator}: whatever uses items the stripes way, the stripes pipe or (in this port) a
 * stripes robot. Handlers hand it what they produce, such as sheared drops.
 */
public interface StripesActivator {
    /**
     * Takes {@code stack} back from a handler, as if it came out of the block in front ({@code from} is the
     * direction the activator faces).
     *
     * @return false if it could not take the stack
     */
    boolean sendItem(ItemStack stack, Direction from);
}
