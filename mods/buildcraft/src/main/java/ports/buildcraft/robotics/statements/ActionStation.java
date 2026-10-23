package ports.buildcraft.robotics.statements;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.robotics.DockingStationPluggable;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.Gate;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.StatementParameterItemStack;

/**
 * Docking station actions for gates on a pipe with a docking station, written for this port. Each applies to every
 * station on the gate's pipe for as long as the gate holds it active:
 * <ul>
 * <li>Provide Items: carriers may take items from the inventories beside the pipe (optionally only those matching
 * the item parameters);</li>
 * <li>Accept Items: carriers may deliver here (optionally only matching items);</li>
 * <li>Drop Items In Pipe: robots unloading here put everything into the item pipe;</li>
 * <li>Forbid Robot: robots other than the one that lives here may not dock;</li>
 * <li>Wake Up: the robot that lives (or is docked) here stops sleeping;</li>
 * <li>Work in Area: robots that live here work in the area or zone of the map location parameter.</li>
 * </ul>
 */
public class ActionStation extends BCStatement implements Action.Internal {
    public enum Kind {
        PROVIDE_ITEMS("gate.action.station.provide_items", "action_station_provide_items", 3),
        ACCEPT_ITEMS("gate.action.station.accept_items", "action_station_accept_items", 3),
        DROP_IN_PIPE("gate.action.station.drop_items_in_pipe", "action_station_drop_in_pipe", 0),
        FORBID_ROBOT("gate.action.station.forbid_robot", "action_station_robot_forbidden", 0),
        WAKE_UP("gate.action.robot.wakeup", "action_robot_wakeup", 0),
        WORK_IN_AREA("gate.action.robot.work_in_area", "action_robot_work_in_area", 1);

        final String translation;
        final String sprite;
        final int parameters;

        Kind(String translation, String sprite, int parameters) {
            this.translation = translation;
            this.sprite = sprite;
            this.parameters = parameters;
        }
    }

    public final Kind kind;
    private final ResourceLocation sprite;

    public ActionStation(Kind kind) {
        super("buildcraft:station." + kind.name().toLowerCase(Locale.ROOT));
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
    public int maxParameters() {
        return kind.parameters;
    }

    @Override
    public int minParameters() {
        return kind == Kind.WORK_IN_AREA ? 1 : 0;
    }

    @Nullable
    @Override
    public StatementParameter createParameter(int index) {
        return index < kind.parameters ? StatementParameterItemStack.EMPTY : null;
    }

    @Override
    public void actionActivate(StatementContainer source, StatementParameter[] parameters) {
        if (!(source instanceof Gate gate)) return;
        List<ItemStack> stacks = new ArrayList<>();
        for (int index = 0; index < kind.parameters; index++) {
            stacks.add(getParam(index, parameters, StatementParameterItemStack.EMPTY).getItemStack());
        }
        for (Direction face : Direction.values()) {
            if (!(gate.getPipe().getPluggable(face) instanceof DockingStationPluggable station)) continue;
            switch (kind) {
                case PROVIDE_ITEMS -> station.actionProvide(stacks);
                case ACCEPT_ITEMS -> station.actionAccept(stacks);
                case DROP_IN_PIPE -> station.actionDropInPipe();
                case FORBID_ROBOT -> station.actionForbid();
                case WAKE_UP -> station.actionWakeUp();
                case WORK_IN_AREA -> station.actionWorkInArea(stacks.get(0));
            }
        }
    }

    @Override
    public ActionStation[] getPossible() {
        return BCRoboticsStatements.ACTIONS;
    }
}
