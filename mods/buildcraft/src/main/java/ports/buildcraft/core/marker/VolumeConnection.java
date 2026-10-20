package ports.buildcraft.core.marker;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import ports.buildcraft.lib.Box;
import ports.buildcraft.lib.marker.MarkerConnection;

/**
 * Upstream {@code VolumeConnection}: volume markers joined along up to three axes, outlining the box
 * that encloses them.
 */
public class VolumeConnection extends MarkerConnection<VolumeConnection> {
    private final Set<BlockPos> makeup = new LinkedHashSet<>();
    private final Box box = new Box();

    public static boolean tryCreateConnection(VolumeSubCache subCache, BlockPos from, BlockPos to) {
        if (canCreateConnection(subCache, from, to)) {
            VolumeConnection connection = new VolumeConnection(subCache);
            connection.makeup.add(from.immutable());
            connection.makeup.add(to.immutable());
            connection.createBox();
            subCache.addConnection(connection);
            return true;
        }
        return false;
    }

    /** Two unconnected markers join when they are in line, in range, and no marker stands between them. */
    public static boolean canCreateConnection(VolumeSubCache subCache, BlockPos from, BlockPos to) {
        Direction directOffset = MarkerCaches.directOffset(from, to);
        if (directOffset == null) return false;
        for (int i = 1; i <= MarkerCaches.MAX_DISTANCE; i++) {
            BlockPos offset = from.relative(directOffset, i);
            if (offset.equals(to)) return true;
            if (subCache.hasLoadedOrUnloadedMarker(offset)) return false;
        }
        return false;
    }

    public VolumeConnection(VolumeSubCache subCache) {
        super(subCache);
    }

    public VolumeConnection(VolumeSubCache subCache, Collection<BlockPos> positions) {
        super(subCache);
        makeup.addAll(positions);
        createBox();
    }

    @Override
    public void removeMarker(BlockPos pos) {
        makeup.remove(pos);
        if (makeup.size() < 2) {
            // The sub-cache drops connections this small.
            makeup.clear();
        }
        createBox();
    }

    public boolean addMarker(BlockPos pos) {
        if (canAddMarker(pos)) {
            makeup.add(pos.immutable());
            createBox();
            subCache.refreshConnection(this);
            return true;
        }
        return false;
    }

    /** A marker joins in line with a member along an axis not yet used, or at a corner of the box. */
    public boolean canAddMarker(BlockPos to) {
        Set<Direction.Axis> taken = getConnectedAxis();
        for (BlockPos from : makeup) {
            Direction direct = MarkerCaches.directOffset(from, to);
            if (direct != null && !taken.contains(direct.getAxis())) return true;
        }
        return !makeup.contains(to) && box.isCorner(to);
    }

    public boolean mergeWith(VolumeConnection other) {
        if (canMergeWith(other)) {
            makeup.addAll(other.makeup);
            other.makeup.clear();
            createBox();
            subCache.refreshConnection(other);
            subCache.refreshConnection(this);
            return true;
        }
        return false;
    }

    /** Two single-axis lines on different axes merge when a pair of their markers lines up on the third axis. */
    public boolean canMergeWith(VolumeConnection other) {
        EnumSet<Direction.Axis> us = getConnectedAxis();
        EnumSet<Direction.Axis> them = other.getConnectedAxis();
        if (us.size() != 1 || them.size() != 1) return false;
        if (us.equals(them)) return false;
        Set<Direction.Axis> blacklisted = EnumSet.copyOf(us);
        blacklisted.addAll(them);
        for (BlockPos from : makeup) {
            for (BlockPos to : other.makeup) {
                Direction offset = MarkerCaches.directOffset(from, to);
                if (offset != null && !blacklisted.contains(offset.getAxis())) return true;
            }
        }
        return false;
    }

    public EnumSet<Direction.Axis> getConnectedAxis() {
        EnumSet<Direction.Axis> taken = EnumSet.noneOf(Direction.Axis.class);
        for (BlockPos a : makeup) {
            for (BlockPos b : makeup) {
                Direction offset = MarkerCaches.directOffset(a, b);
                if (offset != null) taken.add(offset.getAxis());
            }
        }
        return taken;
    }

    @Override
    public Collection<BlockPos> getMarkerPositions() {
        return makeup;
    }

    private void createBox() {
        box.reset();
        for (BlockPos p : makeup) box.extendToEncompass(p);
    }

    public Box getBox() {
        return box.isInitialized() ? new Box(box.min(), box.max()) : new Box();
    }
}
