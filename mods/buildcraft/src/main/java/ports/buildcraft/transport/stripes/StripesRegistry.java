package ports.buildcraft.transport.stripes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.transport.behaviour.StripesBehaviour;

/**
 * Upstream {@code StripesRegistry} with the item handlers {@code BCTransportRegistries} adds, in the same order:
 * plant, shears, pipes and hoe at normal priority, then entity interaction, dispenser, place block and use at low
 * priority. Upstream's only block handler (minecart destroy) is registered but never called there, so it is not
 * ported; its pipe wire handler is commented out upstream.
 */
public final class StripesRegistry {
    public enum Priority {
        HIGH,
        NORMAL,
        LOW
    }

    private static final Map<Priority, List<StripesHandler>> HANDLERS = new EnumMap<>(Priority.class);

    static {
        for (Priority priority : Priority.values()) HANDLERS.put(priority, new ArrayList<>());
        addHandler(StripesHandlers::plant, Priority.NORMAL);
        addHandler(StripesHandlers::shears, Priority.NORMAL);
        addHandler(StripesHandlers::pipes, Priority.NORMAL);
        addHandler(StripesHandlers::entityInteract, Priority.LOW);
        addHandler(StripesHandlers::hoe, Priority.NORMAL);
        addHandler(StripesHandlers::dispenser, Priority.LOW);
        addHandler(StripesHandlers::placeBlock, Priority.LOW);
        addHandler(StripesHandlers::use, Priority.LOW);
    }

    private StripesRegistry() {}

    public static synchronized void addHandler(StripesHandler handler, Priority priority) {
        HANDLERS.get(priority).add(handler);
    }

    /** @return true if a handler used the item, false if nothing was done. */
    public static boolean handleItem(ServerLevel level, BlockPos pos, Direction direction, ItemStack stack, ServerPlayer player,
            StripesBehaviour activator) {
        for (Priority priority : Priority.values()) {
            for (StripesHandler handler : HANDLERS.get(priority)) {
                if (handler.handle(level, pos, direction, stack, player, activator)) return true;
            }
        }
        return false;
    }
}
