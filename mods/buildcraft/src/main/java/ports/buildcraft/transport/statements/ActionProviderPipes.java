package ports.buildcraft.transport.statements;

import java.util.Collection;
import net.minecraft.world.item.DyeColor;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.ActionProvider;
import ports.buildcraft.statement.Gate;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.transport.wire.WireEmitter;

/** Upstream {@code ActionProviderPipes}: the actions a gate's own pipe offers. */
public enum ActionProviderPipes implements ActionProvider {
    INSTANCE;

    @Override
    public void addInternalActions(Collection<Action.Internal> actions, StatementContainer container) {
        if (!(container instanceof Gate gate)) return;
        if (container instanceof WireEmitter) {
            for (DyeColor colour : DyeColor.values()) {
                if (TriggerPipeSignal.doesGateHaveColour(gate, colour)) actions.add(BCTransportStatements.ACTION_PIPE_SIGNAL[colour.getId()]);
            }
        }
        gate.getPipe().behaviour().addInternalActions(actions);
    }
}
