package ports.buildcraft.robotics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Per-level bookkeeping for robots, written for this port: the docking stations robots may look for, and the
 * targets (blocks, dropped items, stations) a robot has claimed so that two robots never chase the same one.
 * Stations add themselves as they tick; entries whose station has gone are dropped when they are looked up.
 * Nothing here is saved: stations re-register on load and robots claim again as they plan.
 */
public final class RobotWorld {
    private static final Map<Level, RobotWorld> WORLDS = new WeakHashMap<>();

    private final Set<StationRef> stations = new LinkedHashSet<>();
    private final Map<Object, RobotEntity> claims = new HashMap<>();

    private RobotWorld() {}

    public static synchronized RobotWorld get(Level level) {
        return WORLDS.computeIfAbsent(level, key -> new RobotWorld());
    }

    void addStation(StationRef ref) {
        stations.add(ref);
    }

    void removeStation(StationRef ref) {
        stations.remove(ref);
    }

    /** Loaded stations within {@code range} blocks of {@code from} that pass {@code filter}, nearest first. */
    public List<DockingStationPluggable> stationsNear(Level level, Vec3 from, double range, Predicate<DockingStationPluggable> filter) {
        List<DockingStationPluggable> found = new ArrayList<>();
        double rangeSqr = range * range;
        for (StationRef ref : List.copyOf(stations)) {
            if (!level.isLoaded(ref.pos())) continue;
            DockingStationPluggable station = ref.resolve(level);
            if (station == null) {
                stations.remove(ref);
                continue;
            }
            if (ref.distanceToSqr(from) <= rangeSqr && filter.test(station)) found.add(station);
        }
        found.sort((a, b) -> Double.compare(a.ref().distanceToSqr(from), b.ref().distanceToSqr(from)));
        return Collections.unmodifiableList(found);
    }

    /** Claims {@code key} for {@code robot}; false if another living robot holds it. */
    public boolean claim(RobotEntity robot, Object key) {
        RobotEntity holder = claims.get(key);
        if (holder != null && holder != robot && holder.isAlive()) return false;
        claims.put(key, robot);
        return true;
    }

    public boolean isClaimedByOther(RobotEntity robot, Object key) {
        RobotEntity holder = claims.get(key);
        return holder != null && holder != robot && holder.isAlive();
    }

    public void release(RobotEntity robot, Object key) {
        claims.remove(key, robot);
    }

    public void releaseAll(RobotEntity robot) {
        claims.values().removeIf(holder -> holder == robot);
    }
}
