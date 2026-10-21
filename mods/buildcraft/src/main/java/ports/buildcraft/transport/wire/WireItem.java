package ports.buildcraft.transport.wire;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.statement.ColourText;

/**
 * Upstream {@code ItemWire}: pipe wire of one colour, placed on a pipe's corner by using it on the pipe.
 * Upstream's sixteen colours were metadata of one item; here each is its own item.
 */
public class WireItem extends Item {
    public final DyeColor colour;

    public WireItem(Properties properties, DyeColor colour) {
        super(properties);
        this.colour = colour;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.buildcraft.wire", ColourText.of(colour));
    }
}
