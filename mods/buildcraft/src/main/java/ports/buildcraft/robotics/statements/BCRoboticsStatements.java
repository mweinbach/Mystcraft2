package ports.buildcraft.robotics.statements;

import java.util.Collection;
import java.util.List;
import net.minecraft.core.Direction;
import ports.buildcraft.robotics.DockingStationPluggable;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.ActionProvider;
import ports.buildcraft.statement.Gate;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementManager;
import ports.buildcraft.statement.Trigger;
import ports.buildcraft.statement.TriggerProvider;

/** The robot triggers and docking station actions, offered to every gate on a pipe that carries a docking station. */
public final class BCRoboticsStatements {
    public static final TriggerRobot TRIGGER_ROBOT_IN_STATION = new TriggerRobot(TriggerRobot.Kind.IN_STATION);
    public static final TriggerRobot TRIGGER_STATION_LINKED = new TriggerRobot(TriggerRobot.Kind.LINKED);
    public static final TriggerRobot TRIGGER_STATION_RESERVED = new TriggerRobot(TriggerRobot.Kind.RESERVED);
    public static final TriggerRobot TRIGGER_ROBOT_SLEEP = new TriggerRobot(TriggerRobot.Kind.SLEEP);
    public static final TriggerRobot[] TRIGGERS = {TRIGGER_ROBOT_IN_STATION, TRIGGER_STATION_LINKED, TRIGGER_STATION_RESERVED, TRIGGER_ROBOT_SLEEP};

    public static final ActionStation ACTION_PROVIDE_ITEMS = new ActionStation(ActionStation.Kind.PROVIDE_ITEMS);
    public static final ActionStation ACTION_ACCEPT_ITEMS = new ActionStation(ActionStation.Kind.ACCEPT_ITEMS);
    public static final ActionStation ACTION_PROVIDE_FLUIDS = new ActionStation(ActionStation.Kind.PROVIDE_FLUIDS);
    public static final ActionStation ACTION_ACCEPT_FLUIDS = new ActionStation(ActionStation.Kind.ACCEPT_FLUIDS);
    public static final ActionStation ACTION_DROP_IN_PIPE = new ActionStation(ActionStation.Kind.DROP_IN_PIPE);
    public static final ActionStation ACTION_FORBID_ROBOT = new ActionStation(ActionStation.Kind.FORBID_ROBOT);
    public static final ActionStation ACTION_WAKE_UP = new ActionStation(ActionStation.Kind.WAKE_UP);
    public static final ActionStation ACTION_WORK_IN_AREA = new ActionStation(ActionStation.Kind.WORK_IN_AREA);
    public static final ActionStation[] ACTIONS = {ACTION_PROVIDE_ITEMS, ACTION_ACCEPT_ITEMS, ACTION_PROVIDE_FLUIDS, ACTION_ACCEPT_FLUIDS,
            ACTION_DROP_IN_PIPE, ACTION_FORBID_ROBOT, ACTION_WAKE_UP, ACTION_WORK_IN_AREA};

    private BCRoboticsStatements() {}

    public static void init() {
        StatementManager.registerTriggerProvider(RoboticsStatementProvider.INSTANCE);
        StatementManager.registerActionProvider(RoboticsStatementProvider.INSTANCE);
    }

    static boolean hasStation(StatementContainer container) {
        if (!(container instanceof Gate gate)) return false;
        for (Direction face : Direction.values()) {
            if (gate.getPipe().getPluggable(face) instanceof DockingStationPluggable) return true;
        }
        return false;
    }

    enum RoboticsStatementProvider implements TriggerProvider, ActionProvider {
        INSTANCE;

        @Override
        public void addInternalTriggers(Collection<Trigger.Internal> triggers, StatementContainer container) {
            if (hasStation(container)) triggers.addAll(List.of(TRIGGERS));
        }

        @Override
        public void addInternalActions(Collection<Action.Internal> actions, StatementContainer container) {
            if (hasStation(container)) actions.addAll(List.of(ACTIONS));
        }
    }
}
