package ports.buildcraft.transport.pluggable;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.transport.PipeBlockEntity;

/** Upstream {@code IItemPluggable}: an item that can be placed onto a pipe face as a pluggable. */
public interface PluggableItem {
    /**
     * @return the pluggable to add to {@code side}, or null if this stack cannot go there. A non-null
     *     result is always added, so the item plays its own effects.
     */
    @Nullable
    PipePluggable onPlace(ItemStack stack, PipeBlockEntity holder, Direction side, Player player, InteractionHand hand);
}
