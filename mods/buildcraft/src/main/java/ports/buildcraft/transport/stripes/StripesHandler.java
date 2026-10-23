package ports.buildcraft.transport.stripes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.transport.behaviour.StripesBehaviour;

/** Upstream {@code IStripesHandlerItem}: one way a stripes pipe can use an item on the block in front of it. */
@FunctionalInterface
public interface StripesHandler {
    /**
     * @param pos the stripes pipe
     * @param stack the stack in {@code player}'s main hand, which the handler may use up
     * @param player the fake player holding {@code stack}; what it holds afterwards goes back into the pipe
     * @return true if the item was used, in which case nothing drops
     */
    boolean handle(ServerLevel level, BlockPos pos, Direction direction, ItemStack stack, ServerPlayer player, StripesBehaviour activator);
}
