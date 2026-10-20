package ports.buildcraft.core.marker;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import ports.buildcraft.lib.marker.MarkerSubCache;

/** Upstream {@code PathSubCache}. */
public class PathSubCache extends MarkerSubCache<PathConnection> {
    public PathSubCache(Level level) {
        super(level);
    }

    @Override
    public boolean tryConnect(BlockPos from, BlockPos to) {
        PathConnection conFrom = getConnection(from);
        PathConnection conTo = getConnection(to);
        if (conFrom == null) {
            if (conTo == null) return PathConnection.tryCreateConnection(this, from, to);
            return conTo.addMarker(from, to);
        }
        if (conTo == null) return conFrom.addMarker(from, to);
        return conFrom.mergeWith(conTo, from, to);
    }

    @Override
    public boolean canConnect(BlockPos from, BlockPos to) {
        PathConnection conFrom = getConnection(from);
        PathConnection conTo = getConnection(to);
        if (conFrom == null) {
            if (conTo == null) return true;
            return conTo.canAddMarker(from, to);
        }
        if (conTo == null) return conFrom.canAddMarker(from, to);
        return conFrom.canMergeWith(conTo, from, to);
    }

    /** Every other marker in range that could join this one in either direction. */
    @Override
    public List<BlockPos> getValidConnections(BlockPos from) {
        List<BlockPos> list = new ArrayList<>();
        int maxLengthSquared = MarkerCaches.MAX_DISTANCE * MarkerCaches.MAX_DISTANCE;
        for (BlockPos pos : getAllMarkers()) {
            if (pos.equals(from)) continue;
            if (pos.distSqr(from) > maxLengthSquared) continue;
            if (canConnect(from, pos) || canConnect(pos, from)) list.add(pos);
        }
        return list;
    }

    @Override
    protected PathConnection createConnection(List<BlockPos> positions) {
        return new PathConnection(this, positions);
    }
}
