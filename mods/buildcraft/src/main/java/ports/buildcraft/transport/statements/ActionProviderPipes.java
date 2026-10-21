package ports.buildcraft.transport.statements;

import java.util.Collection;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.ActionProvider;
import ports.buildcraft.statement.Gate;
import ports.buildcraft.statement.StatementContainer;

/** Upstream {@code ActionProviderPipes}: the actions a gate's own pipe offers. */
public enum ActionProviderPipes implements ActionProvider {
    INSTANCE;

    @Override
    public void addInternalActions(Collection<Action.Internal> actions, StatementContainer container) {
        if (container instanceof Gate gate) gate.getPipe().behaviour().addInternalActions(actions);
    }
}
