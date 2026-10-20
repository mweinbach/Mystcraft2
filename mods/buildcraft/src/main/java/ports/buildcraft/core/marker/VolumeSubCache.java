package ports.buildcraft.core.marker;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import ports.buildcraft.lib.marker.MarkerSubCache;

/** Upstream {@code VolumeSubCache}. */
public class VolumeSubCache extends MarkerSubCache<VolumeConnection> {
    public VolumeSubCache(Level level) {
        super(level);
    }

    @Override
    public boolean tryConnect(BlockPos from, BlockPos to) {
        VolumeConnection fromConnection = getConnection(from);
        VolumeConnection toConnection = getConnection(to);
        if (fromConnection == null) {
            if (toConnection == null) return VolumeConnection.tryCreateConnection(this, from, to);
            return toConnection.addMarker(from);
        }
        if (toConnection == null) return fromConnection.addMarker(to);
        return fromConnection.mergeWith(toConnection);
    }

    @Override
    public boolean canConnect(BlockPos from, BlockPos to) {
        VolumeConnection fromConnection = getConnection(from);
        VolumeConnection toConnection = getConnection(to);
        if (fromConnection == null) {
            if (toConnection == null) return VolumeConnection.canCreateConnection(this, from, to);
            return toConnection.canAddMarker(from);
        }
        if (toConnection == null) return fromConnection.canAddMarker(to);
        return fromConnection.canMergeWith(toConnection);
    }

    /** The nearest marker along each axis this marker's connection does not use yet, if it can connect. */
    @Override
    public List<BlockPos> getValidConnections(BlockPos from) {
        VolumeConnection existing = getConnection(from);
        Set<Direction.Axis> taken = EnumSet.noneOf(Direction.Axis.class);
        if (existing != null) taken.addAll(existing.getConnectedAxis());
        List<BlockPos> valids = new ArrayList<>();
        for (Direction face : Direction.values()) {
            if (taken.contains(face.getAxis())) continue;
            for (int i = 1; i <= MarkerCaches.MAX_DISTANCE; i++) {
                BlockPos toTry = from.relative(face, i);
                if (hasLoadedOrUnloadedMarker(toTry)) {
                    if (canConnect(from, toTry)) valids.add(toTry);
                    break;
                }
            }
        }
        return valids;
    }

    @Override
    protected VolumeConnection createConnection(List<BlockPos> positions) {
        return new VolumeConnection(this, positions);
    }
}
