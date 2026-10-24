package ports.gridwright.grid;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Grid Power ledger for one server. GP is a rate, not stored energy: every loaded producer adds
 * its current output and every working consumer adds its demand, each server tick, to the grid of its
 * owner (a player, or the scoreboard team that player is on). A grid is powered while its capacity
 * covers its demand; when demand exceeds capacity every consumer on that grid stops, not just the
 * newest one.
 *
 * <p>Members report during their own block entity tick, so the order they tick in must not matter.
 * Each tick therefore reads the totals of the previous, completed tick. Unloaded blocks stop ticking
 * and drop out after one tick; there is no registration to leak.
 *
 * <p>Producers of the same type have diminishing returns: within one grid the k-th strongest producer
 * of a type (counting from 0) contributes {@code output * 32 / (32 + k)}, so mixing types pays more
 * than repeating one.
 */
public final class GridManager {

    /** Diminishing-returns constant; see the class comment. */
    static final double FALLOFF = 32.0;

    private static GridManager current;

    private final MinecraftServer server;
    private int tick = Integer.MIN_VALUE;
    private Map<String, Ledger> building = new HashMap<>();
    private Map<String, GridSnapshot> completed = Map.of();

    private GridManager(MinecraftServer server) {
        this.server = server;
    }

    /** The ledger of {@code server}; a new server (or a restarted integrated server) starts empty. */
    public static synchronized GridManager get(MinecraftServer server) {
        if (current == null || current.server != server) current = new GridManager(server);
        return current;
    }

    /** Adds {@code gp} of production from one producer of {@code type} to {@code grid} for this tick. */
    public void addProduction(String grid, String type, double gp) {
        roll();
        if (gp > 0) building.computeIfAbsent(grid, key -> new Ledger()).produce(type, gp);
        else building.computeIfAbsent(grid, key -> new Ledger()).producers++;
    }

    /** Adds one consumer's {@code gp} of demand to {@code grid} for this tick. */
    public void addDemand(String grid, double gp) {
        roll();
        Ledger ledger = building.computeIfAbsent(grid, key -> new Ledger());
        ledger.demand += Math.max(0, gp);
        ledger.consumers++;
    }

    /** The totals {@code grid} reached in the last completed tick. */
    public GridSnapshot snapshot(String grid) {
        roll();
        return completed.getOrDefault(grid, GridSnapshot.EMPTY);
    }

    private void roll() {
        int now = server.getTickCount();
        if (now == tick) return;
        Map<String, GridSnapshot> finished = new HashMap<>();
        // Totals from an older tick are stale: those members did not tick last tick.
        if (now == tick + 1) building.forEach((grid, ledger) -> finished.put(grid, ledger.finish()));
        completed = finished;
        building = new HashMap<>();
        tick = now;
    }

    /** Total capacity of producers of one type after diminishing returns. */
    public static double combine(List<Double> outputs) {
        List<Double> sorted = new ArrayList<>(outputs);
        sorted.sort((a, b) -> Double.compare(b, a));
        double total = 0;
        for (int k = 0; k < sorted.size(); k++) total += sorted.get(k) * FALLOFF / (FALLOFF + k);
        return total;
    }

    private static final class Ledger {
        final Map<String, List<Double>> production = new HashMap<>();
        double demand;
        int producers;
        int consumers;

        void produce(String type, double gp) {
            production.computeIfAbsent(type, key -> new ArrayList<>()).add(gp);
            producers++;
        }

        GridSnapshot finish() {
            double capacity = 0;
            for (List<Double> outputs : production.values()) capacity += combine(outputs);
            return new GridSnapshot(capacity, demand, producers, consumers);
        }
    }
}
