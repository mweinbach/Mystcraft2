package ports.buildcraft.core.marker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import ports.buildcraft.lib.marker.MarkerConnection;
import ports.buildcraft.lib.marker.MarkerSubCache;

/**
 * Upstream {@code PathConnection}: an ordered path of markers, extended at either end, which may close
 * into a loop. Its positions list the loop's first marker again at the end.
 */
public class PathConnection extends MarkerConnection<PathConnection> {
    private final Deque<BlockPos> positions = new ArrayDeque<>();
    private boolean loop = false;

    public static boolean tryCreateConnection(PathSubCache subCache, BlockPos from, BlockPos to) {
        PathConnection connection = new PathConnection(subCache);
        connection.positions.add(from.immutable());
        connection.positions.add(to.immutable());
        subCache.addConnection(connection);
        return true;
    }

    public PathConnection(MarkerSubCache<PathConnection> subCache) {
        super(subCache);
    }

    public PathConnection(PathSubCache subCache, List<BlockPos> positions) {
        super(subCache);
        for (BlockPos p : positions) {
            if (p.equals(this.positions.peekFirst())) {
                loop = true;
                break;
            }
            this.positions.addLast(p);
        }
    }

    public boolean isLoop() {
        return loop;
    }

    @Override
    public void removeMarker(BlockPos pos) {
        if (positions.getFirst().equals(pos)) {
            positions.removeFirst();
            loop = false;
            if (positions.size() < 2) positions.clear();
            subCache.refreshConnection(this);
        } else if (positions.getLast().equals(pos)) {
            positions.removeLast();
            loop = false;
            if (positions.size() < 2) positions.clear();
            subCache.refreshConnection(this);
        } else if (positions.contains(pos)) {
            List<BlockPos> a = new ArrayList<>();
            List<BlockPos> b = new ArrayList<>();
            boolean hasReached = false;
            for (BlockPos p : positions) {
                if (p.equals(pos)) hasReached = true;
                else if (hasReached) b.add(p);
                else a.add(p);
            }
            loop = false;
            PathConnection conA = new PathConnection(subCache);
            PathConnection conB = new PathConnection(subCache);
            conA.positions.addAll(a);
            conB.positions.addAll(b);
            positions.clear();
            subCache.destroyConnection(this);
            subCache.addConnection(conA);
            subCache.addConnection(conB);
        }
    }

    public boolean addMarker(BlockPos from, BlockPos toAdd) {
        if (loop) return false;
        boolean contains = positions.contains(toAdd);
        if (positions.getFirst().equals(from)) {
            if (positions.getLast().equals(toAdd)) loop = true;
            else if (!contains) positions.addFirst(toAdd.immutable());
            else return false;
            subCache.refreshConnection(this);
            return true;
        } else if (positions.getLast().equals(from)) {
            if (positions.getFirst().equals(toAdd)) loop = true;
            else if (!contains) positions.addLast(toAdd.immutable());
            else return false;
            subCache.refreshConnection(this);
            return true;
        }
        return false;
    }

    public boolean canAddMarker(BlockPos from, BlockPos toAdd) {
        if (loop) return false;
        boolean contains = positions.contains(toAdd);
        if (positions.getFirst().equals(from) || positions.getLast().equals(from)) {
            return !contains || positions.getLast().equals(toAdd);
        }
        return false;
    }

    public boolean mergeWith(PathConnection conTo, BlockPos from, BlockPos to) {
        if (loop || conTo.loop) return false;
        if (conTo == this) {
            if (positions.size() <= 2) return false;
            if (positions.getFirst().equals(to) && positions.getLast().equals(from)) {
                loop = true;
                subCache.refreshConnection(this);
                return true;
            }
            return false;
        }
        if (positions.getLast().equals(from) && conTo.positions.getFirst().equals(to)) {
            subCache.destroyConnection(conTo);
            positions.addAll(conTo.positions);
            subCache.refreshConnection(this);
            return true;
        }
        return false;
    }

    public boolean canMergeWith(PathConnection conTo, BlockPos from, BlockPos to) {
        if (loop || conTo.loop) return false;
        if (conTo == this) return positions.size() > 2 && positions.getFirst().equals(to) && positions.getLast().equals(from);
        return positions.getLast().equals(from) && conTo.positions.getFirst().equals(to);
    }

    @Override
    public List<BlockPos> getMarkerPositions() {
        List<BlockPos> list = new ArrayList<>(positions);
        if (loop && !positions.isEmpty()) list.add(positions.getFirst());
        return List.copyOf(list);
    }

    public void reverseDirection() {
        Deque<BlockPos> reversed = new ArrayDeque<>();
        while (!positions.isEmpty()) reversed.addFirst(positions.removeFirst());
        positions.addAll(reversed);
        subCache.refreshConnection(this);
    }
}
