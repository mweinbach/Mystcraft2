package ports.buildcraft.core;

import net.minecraft.world.item.Item;

/**
 * Upstream {@code ItemWrench_Neptune}. Blocks read the wrench themselves when used, so the item only
 * needs to be recognisable; it is also added to the {@code c:wrenches} and {@code forge:tools/wrench} tags.
 */
public class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties);
    }
}
