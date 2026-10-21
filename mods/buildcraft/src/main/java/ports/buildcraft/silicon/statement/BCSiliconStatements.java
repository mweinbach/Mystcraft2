package ports.buildcraft.silicon.statement;

import java.util.Collection;
import net.minecraft.core.Direction;
import ports.buildcraft.silicon.plug.PluggablePulsar;
import ports.buildcraft.silicon.plug.PluggableTimer;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.ActionProvider;
import ports.buildcraft.statement.Gate;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementManager;
import ports.buildcraft.statement.Trigger;
import ports.buildcraft.statement.TriggerProvider;

/**
 * Upstream {@code BCSiliconStatements} for the pulsar and timer. Light-sensor triggers stay out until that pluggable
 * is ported. Providers stand in for upstream's pipe statement events, which the port does not bus: a timer offers its
 * triggers to every gate on its pipe, and a pulsar offers its actions only on its own face.
 */
public final class BCSiliconStatements {
    public static final TriggerTimer TRIGGER_TIMER_SHORT = new TriggerTimer(TriggerTimer.Duration.SHORT);
    public static final TriggerTimer TRIGGER_TIMER_MEDIUM = new TriggerTimer(TriggerTimer.Duration.MEDIUM);
    public static final TriggerTimer TRIGGER_TIMER_LONG = new TriggerTimer(TriggerTimer.Duration.LONG);
    public static final TriggerTimer[] TRIGGER_TIMER = {TRIGGER_TIMER_SHORT, TRIGGER_TIMER_MEDIUM, TRIGGER_TIMER_LONG};

    public static final ActionPowerPulsar ACTION_PULSAR_CONSTANT = new ActionPowerPulsar(true);
    public static final ActionPowerPulsar ACTION_PULSAR_SINGLE = new ActionPowerPulsar(false);
    public static final ActionPowerPulsar[] ACTION_PULSAR = {ACTION_PULSAR_CONSTANT, ACTION_PULSAR_SINGLE};

    private BCSiliconStatements() {}

    public static void init() {
        StatementManager.registerTriggerProvider(SiliconStatementProvider.INSTANCE);
        StatementManager.registerActionProvider(SiliconStatementProvider.INSTANCE);
    }

    enum SiliconStatementProvider implements TriggerProvider, ActionProvider {
        INSTANCE;

        @Override
        public void addInternalTriggers(Collection<Trigger.Internal> triggers, StatementContainer container) {
            if (!(container instanceof Gate gate)) return;
            for (Direction face : Direction.values()) {
                if (gate.getPipe().getPluggable(face) instanceof PluggableTimer) {
                    triggers.add(TRIGGER_TIMER_SHORT);
                    triggers.add(TRIGGER_TIMER_MEDIUM);
                    triggers.add(TRIGGER_TIMER_LONG);
                    return;
                }
            }
        }

        @Override
        public void addInternalSidedActions(Collection<Action.InternalSided> actions, StatementContainer container, Direction side) {
            if (!(container instanceof Gate gate)) return;
            if (gate.getPipe().getPluggable(side) instanceof PluggablePulsar) {
                actions.add(ACTION_PULSAR_CONSTANT);
                actions.add(ACTION_PULSAR_SINGLE);
            }
        }
    }
}
