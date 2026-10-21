package ports.buildcraft.core.statements;

import java.util.Collection;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.lib.Controllable;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.ActionProvider;
import ports.buildcraft.statement.RedstoneStatementContainer;
import ports.buildcraft.statement.StatementContainer;

/** Upstream {@code CoreActionProvider}: redstone on a redstone gate, and machine control for a controllable neighbour. */
public enum CoreActionProvider implements ActionProvider {
    INSTANCE;

    @Override
    public void addInternalActions(Collection<Action.Internal> actions, StatementContainer container) {
        if (container instanceof RedstoneStatementContainer) actions.add(BCCoreStatements.ACTION_REDSTONE);
    }

    @Override
    public void addExternalActions(Collection<Action.External> actions, Direction side, BlockEntity tile) {
        if (!(tile instanceof Controllable controllable)) return;
        for (ActionMachineControl action : BCCoreStatements.ACTION_MACHINE_CONTROL) {
            if (controllable.acceptsControlMode(action.mode)) actions.add(action);
        }
    }
}
