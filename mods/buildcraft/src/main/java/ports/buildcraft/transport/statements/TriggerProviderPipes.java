package ports.buildcraft.transport.statements;

import java.util.Collection;
import net.minecraft.world.item.DyeColor;
import ports.buildcraft.statement.Gate;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.Trigger;
import ports.buildcraft.statement.TriggerProvider;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.flow.PowerFlow;

/** Upstream {@code TriggerProviderPipes}: the triggers a gate's own pipe offers. */
public enum TriggerProviderPipes implements TriggerProvider {
    INSTANCE;

    @Override
    public void addInternalTriggers(Collection<Trigger.Internal> triggers, StatementContainer container) {
        if (!(container instanceof Gate gate)) return;
        PipeBlockEntity pipe = gate.getPipe();
        for (DyeColor colour : DyeColor.values()) {
            if (TriggerPipeSignal.doesGateHaveColour(gate, colour)) {
                triggers.add(BCTransportStatements.TRIGGER_PIPE_SIGNAL[colour.getId() * 2]);
                triggers.add(BCTransportStatements.TRIGGER_PIPE_SIGNAL[colour.getId() * 2 + 1]);
            }
        }
        pipe.flow().addInternalTriggers(triggers);
        if (pipe.flow() instanceof PowerFlow) triggers.add(BCTransportStatements.TRIGGER_POWER_REQUESTED);
    }
}
