package ports.buildcraft.core;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.statement.ColourText;

/**
 * Upstream {@code ItemPaintbrush_BC8} as a colour only. One item is clean and each dye colour is its own item,
 * where upstream used metadata. Block painting and the 64-use durability are not part of the zone planner.
 */
public class PaintbrushItem extends Item {
    @Nullable public final DyeColor colour;

    public PaintbrushItem(@Nullable DyeColor colour, Properties properties) {
        super(properties);
        this.colour = colour;
    }

    public static boolean isPaintbrush(ItemStack stack) {
        return stack.getItem() instanceof PaintbrushItem;
    }

    /** The dye a coloured brush applies, or null for a clean brush or any other item. */
    @Nullable
    public static DyeColor colorOf(ItemStack stack) {
        return stack.getItem() instanceof PaintbrushItem brush ? brush.colour : null;
    }

    /** Upstream prefixed the item name with the colour, in its tooltip colour. */
    @Override
    public Component getName(ItemStack stack) {
        return colour == null ? super.getName(stack) : ColourText.of(colour).append(" ").append(super.getName(stack));
    }
}
