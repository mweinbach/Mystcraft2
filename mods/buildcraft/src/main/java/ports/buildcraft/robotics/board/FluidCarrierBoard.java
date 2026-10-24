package ports.buildcraft.robotics.board;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.Tank;
import ports.buildcraft.robotics.DockingStationPluggable;
import ports.buildcraft.robotics.Progress;
import ports.buildcraft.robotics.RobotEntity;
import ports.buildcraft.robotics.StationRef;

/**
 * The tank robot (upstream's fluid carrier), written for this port: the {@link CarrierBoard} for fluids. It fills its
 * tank of {@link RobotEntity#TANK_CAPACITY} mB at stations whose gate is "Provide Fluids" (from the tanks beside their
 * pipe, within the gate's filters) and empties it at stations whose gate is "Accept Fluids". Stations without such
 * gates are left alone; with nothing to move it rests at home.
 */
public class FluidCarrierBoard extends RobotBoard {
    public static final int IDLE_TICKS = 40;

    @Nullable
    private StationRef target;
    /** Stations that turned the current load away, skipped until the robot is empty again. */
    private final Set<StationRef> refused = new HashSet<>();

    public FluidCarrierBoard(RobotEntity robot) {
        super(robot);
    }

    @Override
    public void tick() {
        if (robot.tank().isEmpty()) {
            refused.clear();
            load();
        } else {
            deliver();
        }
    }

    private void load() {
        if (target == null) {
            target = nearest(station -> station.canProvideFluid(null));
            if (target == null) {
                idleAtHome(IDLE_TICKS);
                return;
            }
        }
        Progress docking = robot.goDock(target);
        if (docking == Progress.FAILED) {
            target = null;
            return;
        }
        if (docking == Progress.WORKING) return;
        DockingStationPluggable station = robot.dockedStation();
        Tank tank = robot.tank();
        while (station != null && tank.space() > 0) {
            FluidAmount taken = station.provideFluid(tank.isEmpty() ? null : tank.fluid(), tank.space());
            if (taken.isEmpty() || tank.fill(taken.fluid(), (int) taken.amount(), false) == 0) break;
        }
        StationRef loadedAt = target;
        target = null;
        if (tank.isEmpty()) idleAtHome(IDLE_TICKS);
        else refused.add(loadedAt);
    }

    private void deliver() {
        Tank tank = robot.tank();
        if (target == null) {
            target = nearest(station -> !refused.contains(station.ref()) && station.isAcceptingFluids() && station.acceptsFluid(robot, tank.fluid()));
            if (target == null) {
                if (idleAtHome(IDLE_TICKS)) refused.clear();
                return;
            }
        }
        Progress docking = robot.goDock(target);
        if (docking == Progress.FAILED) {
            refused.add(target);
            target = null;
            return;
        }
        if (docking == Progress.WORKING) return;
        DockingStationPluggable station = robot.dockedStation();
        long filled = station == null ? 0 : station.unloadFluid(robot, tank.fluid(), tank.amount());
        if (filled > 0) tank.drain(null, (int) filled, false);
        if (!tank.isEmpty()) refused.add(target);
        target = null;
    }

    @Nullable
    private StationRef nearest(Predicate<DockingStationPluggable> filter) {
        List<DockingStationPluggable> stations = world().stationsIn(level(), robot.workArea(), robot.position(),
                station -> station.isFreeFor(robot) && filter.test(station));
        return stations.isEmpty() ? null : stations.get(0).ref();
    }

    @Override
    public void reset() {
        target = null;
        refused.clear();
    }
}
