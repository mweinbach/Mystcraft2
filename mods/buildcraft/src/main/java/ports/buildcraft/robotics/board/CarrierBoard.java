package ports.buildcraft.robotics.board;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.robotics.DockingStationPluggable;
import ports.buildcraft.robotics.Progress;
import ports.buildcraft.robotics.RobotEntity;
import ports.buildcraft.robotics.StationRef;
import ports.buildcraft.robotics.WorkArea;

/**
 * The carrier, written for this port: moves items between docking stations in its work area. It loads at stations
 * whose gate is "Provide Items" (taking from the inventories beside their pipe, within the gate's filters) and
 * delivers to stations whose gate is "Accept Items" or "Drop Items In Pipe". Stations without such gates are left
 * alone, so a carrier only does what its gates allow; with nothing to move it rests at home.
 */
public class CarrierBoard extends RobotBoard {
    public static final int IDLE_TICKS = 40;

    @Nullable
    private StationRef target;
    /** Stations that turned the current load away, skipped until the robot is empty again. */
    private final Set<StationRef> refused = new HashSet<>();

    public CarrierBoard(RobotEntity robot) {
        super(robot);
    }

    @Override
    public void tick() {
        if (robot.isInventoryEmpty()) {
            refused.clear();
            load();
        } else {
            deliver();
        }
    }

    private void load() {
        Predicate<ItemStack> fits = robot::canHold;
        if (target == null) {
            target = nearest(station -> station.canProvide(fits));
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
        if (station != null) {
            while (!robot.isInventoryFull()) {
                ItemStack taken = station.provide(fits, 64);
                if (taken.isEmpty()) break;
                ItemStack left = robot.addToInventory(taken);
                if (!left.isEmpty()) {
                    for (var port : station.inventories()) {
                        left = port.insert(left, false);
                        if (left.isEmpty()) break;
                    }
                    if (!left.isEmpty()) robot.spawnAtLocation(left);
                    break;
                }
            }
        }
        StationRef loadedAt = target;
        target = null;
        if (robot.isInventoryEmpty()) idleAtHome(IDLE_TICKS);
        else refused.add(loadedAt);
    }

    private void deliver() {
        if (target == null) {
            target = nearest(station -> !refused.contains(station.ref()) && (station.isAccepting() || station.isDroppingInPipe())
                    && acceptsSomething(station));
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
        Progress unload = robot.unloadOne();
        if (unload == Progress.WORKING) return;
        if (!robot.isInventoryEmpty()) refused.add(target);
        target = null;
    }

    private boolean acceptsSomething(DockingStationPluggable station) {
        for (int slot = 0; slot < robot.inventory().getContainerSize(); slot++) {
            ItemStack stack = robot.inventory().getItem(slot);
            if (!stack.isEmpty() && station.accepts(robot, stack)) return true;
        }
        return false;
    }

    @Nullable
    private StationRef nearest(Predicate<DockingStationPluggable> filter) {
        WorkArea area = robot.workArea();
        List<DockingStationPluggable> stations = world().stationsNear(level(), robot.position(), area.radius() * 2 + 1,
                station -> area.contains(station.ref().pos()) && station.isFreeFor(robot) && filter.test(station));
        return stations.isEmpty() ? null : stations.get(0).ref();
    }

    @Override
    public void reset() {
        target = null;
        refused.clear();
    }
}
