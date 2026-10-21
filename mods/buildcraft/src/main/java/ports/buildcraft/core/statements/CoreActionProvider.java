package ports.buildcraft.core.statements;

import java.util.Collection;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.ActionProvider;
import ports.buildcraft.statement.RedstoneStatementContainer;
import ports.buildcraft.statement.StatementContainer;

/** Upstream {@code CoreActionProvider}, without machine control: no ported machine is controllable yet. */
public enum CoreActionProvider implements ActionProvider {
    INSTANCE;

    @Override
    public void addInternalActions(Collection<Action.Internal> actions, StatementContainer container) {
        if (container instanceof RedstoneStatementContainer) actions.add(BCCoreStatements.ACTION_REDSTONE);
    }
}
