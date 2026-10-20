package ports.buildcraft.lib.marker;

import java.util.Collection;
import net.minecraft.core.BlockPos;

/** Upstream {@code MarkerConnection}: a group of markers joined together in one sub-cache. */
public abstract class MarkerConnection<C extends MarkerConnection<C>> {
    public final MarkerSubCache<C> subCache;

    protected MarkerConnection(MarkerSubCache<C> subCache) {
        this.subCache = subCache;
    }

    /**
     * Removes the marker from this connection, splitting or emptying it if what is left is invalid. Call
     * through {@link MarkerSubCache#removeMarker}.
     */
    public abstract void removeMarker(BlockPos pos);

    public abstract Collection<BlockPos> getMarkerPositions();
}
