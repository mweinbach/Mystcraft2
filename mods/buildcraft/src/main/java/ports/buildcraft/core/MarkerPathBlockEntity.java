package ports.buildcraft.core;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.core.marker.MarkerCaches;
import ports.buildcraft.core.marker.PathConnection;
import ports.buildcraft.core.marker.PathSubCache;
import ports.buildcraft.lib.marker.MarkerBlockEntity;
import ports.buildcraft.lib.marker.MarkerCache;

/** Upstream {@code TileMarkerPath}: a path mark, joined to others into an ordered path or loop. */
public class MarkerPathBlockEntity extends MarkerBlockEntity<PathConnection> {
    public MarkerPathBlockEntity(BlockPos pos, BlockState state) {
        super(BCCore.MARKER_PATH_TYPE.get(), pos, state);
    }

    @Override
    public MarkerCache<PathSubCache> getCache() {
        return MarkerCaches.PATH;
    }

    /** Upstream {@code IPathProvider.getPath}: the path through this mark, ending at its start again if it loops. */
    public List<BlockPos> getPath() {
        PathConnection connection = getCurrentConnection();
        return connection == null ? List.of() : connection.getMarkerPositions();
    }

    public void removeFromWorld() {
        if (level == null) return;
        for (BlockPos pos : getPath()) level.destroyBlock(pos, true);
    }

    public void reverseDirection() {
        if (level == null || level.isClientSide) return;
        PathConnection connection = getCurrentConnection();
        if (connection != null) connection.reverseDirection();
    }
}
