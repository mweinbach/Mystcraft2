package ports.buildcraft.statement;

import java.util.Collection;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Upstream {@code ITriggerProvider}: offers triggers for a container and its neighbours. */
public interface TriggerProvider {
    default void addInternalTriggers(Collection<Trigger.Internal> triggers, StatementContainer container) {}

    default void addInternalSidedTriggers(Collection<Trigger.InternalSided> triggers, StatementContainer container, Direction side) {}

    /** @param side the face of the container that {@code tile} is on */
    default void addExternalTriggers(Collection<Trigger.External> triggers, Direction side, BlockEntity tile) {}
}
