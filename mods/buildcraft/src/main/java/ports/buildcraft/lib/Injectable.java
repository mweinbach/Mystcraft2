package ports.buildcraft.lib;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/** Upstream {@code IInjectable}: something that accepts moving items, such as a transport pipe. */
public interface Injectable {
    boolean canInjectItems(Direction from);

    /**
     * @param from the face of this block the item enters through
     * @param colour the item's colour, as a lens painted it, or null
     * @param speed the item's speed in blocks per tick, or 0 for the pipe's default
     * @return the part of {@code stack} that was not accepted
     */
    ItemStack injectItem(ItemStack stack, boolean doAdd, Direction from, @Nullable DyeColor colour, double speed);
}
