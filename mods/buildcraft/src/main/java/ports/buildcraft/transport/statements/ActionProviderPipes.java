package ports.buildcraft.transport.statements;

import java.util.Collection;
import java.util.Collections;
import net.minecraft.world.item.DyeColor;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.ActionProvider;
import ports.buildcraft.statement.Gate;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeDefinition;
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
        PipeDefinition definition = gate.getPipe().definition();
        if (definition == BCTransport.IRON_POWER) Collections.addAll(actions, BCTransportStatements.ACTION_IRON_POWER_LIMIT);
        if (definition == BCTransport.DIAMOND_POWER) Collections.addAll(actions, BCTransportStatements.ACTION_DIAMOND_POWER_LIMIT);
    }
}
