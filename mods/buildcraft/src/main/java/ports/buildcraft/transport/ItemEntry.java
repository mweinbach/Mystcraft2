package ports.buildcraft.transport;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/** Upstream {@code PipeEventItem.ItemEntry}: part of a stack and, once split, its chosen exits. */
public final class ItemEntry {
    public final ItemStack stack;
    public final Direction from;
    @Nullable
    public List<Direction> to;

    public ItemEntry(ItemStack stack, Direction from) {
        this.stack = stack;
        this.from = from;
    }
}
