package ports.buildcraft.robotics.statements;

import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ports.buildcraft.robotics.DockingStationPluggable;
import ports.buildcraft.robotics.RobotEntity;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.Gate;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.Trigger;

/**
 * Robot triggers for gates on a pipe with a docking station, written for this port. Each is true when any station on
 * the gate's pipe matches: a robot is docked there, a robot calls it home, a robot is on its way to it, or the robot
 * that calls it home is asleep.
 */
public class TriggerRobot extends BCStatement implements Trigger.Internal {
    public enum Kind {
        IN_STATION("gate.trigger.robot.in.station", "trigger_robot_in_station", DockingStationPluggable::hasDockedRobot),
        LINKED("gate.trigger.robot.linked", "trigger_robot_linked", DockingStationPluggable::isLinked),
        RESERVED("gate.trigger.robot.reserved", "trigger_robot_reserved", DockingStationPluggable::isReserved),
        SLEEP("gate.trigger.robot.sleep", "trigger_robot_sleep", station -> {
            RobotEntity robot = station.linkedRobot();
            return robot != null && robot.isSleeping();
        });

        final String translation;
        final String sprite;
        final Predicate<DockingStationPluggable> test;

        Kind(String translation, String sprite, Predicate<DockingStationPluggable> test) {
            this.translation = translation;
            this.sprite = sprite;
            this.test = test;
        }
    }

    public final Kind kind;
    private final ResourceLocation sprite;

    public TriggerRobot(Kind kind) {
        super("buildcraft:robot." + kind.name().toLowerCase(Locale.ROOT));
        this.kind = kind;
        this.sprite = sprite(kind.sprite);
    }

    @Override
    public Component getDescription() {
        return Component.translatable(kind.translation);
    }

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public boolean isTriggerActive(StatementContainer source, StatementParameter[] parameters) {
        if (!(source instanceof Gate gate)) return false;
        for (Direction face : Direction.values()) {
            if (gate.getPipe().getPluggable(face) instanceof DockingStationPluggable station && kind.test.test(station)) return true;
        }
        return false;
    }

    @Override
    public TriggerRobot[] getPossible() {
        return BCRoboticsStatements.TRIGGERS;
    }
}
