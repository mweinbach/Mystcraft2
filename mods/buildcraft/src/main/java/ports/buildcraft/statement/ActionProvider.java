package ports.buildcraft.statement;

import java.util.Collection;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Upstream {@code IActionProvider}: offers actions for a container and its neighbours. */
public interface ActionProvider {
    default void addInternalActions(Collection<Action.Internal> actions, StatementContainer container) {}

    default void addInternalSidedActions(Collection<Action.InternalSided> actions, StatementContainer container, Direction side) {}

    /** @param side the face of the container that {@code tile} is on */
    default void addExternalActions(Collection<Action.External> actions, Direction side, BlockEntity tile) {}
}
