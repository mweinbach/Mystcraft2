package ports.buildcraft.core;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.core.marker.MarkerCaches;
import ports.buildcraft.core.marker.VolumeConnection;
import ports.buildcraft.core.marker.VolumeSubCache;
import ports.buildcraft.lib.AreaProvider;
import ports.buildcraft.lib.Box;
import ports.buildcraft.lib.marker.MarkerBlockEntity;
import ports.buildcraft.lib.marker.MarkerCache;
import ports.buildcraft.lib.marker.MarkerSubCache;

/**
 * Upstream {@code TileMarkerVolume}: a land mark. Connected marks outline a box that a quarry placed
 * against it takes as its area; a powered mark shows signal lasers along its free axes.
 */
public class MarkerVolumeBlockEntity extends MarkerBlockEntity<VolumeConnection> implements AreaProvider {
    private boolean showSignals = false;

    public MarkerVolumeBlockEntity(BlockPos pos, BlockState state) {
        super(BCCore.MARKER_VOLUME_TYPE.get(), pos, state);
    }

    @Override
    public MarkerCache<VolumeSubCache> getCache() {
        return MarkerCaches.VOLUME;
    }

    public boolean isShowingSignals() {
        return showSignals;
    }

    public void switchSignals() {
        if (level == null || level.isClientSide) return;
        showSignals = !showSignals;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    /** Connects to every marker it can reach, then takes in any marker standing on a corner of the new box. */
    public void onManualConnectionAttempt() {
        MarkerSubCache<VolumeConnection> cache = getLocalCache();
        for (BlockPos other : cache.getValidConnections(worldPosition)) cache.tryConnect(worldPosition, other);
        VolumeConnection connection = getCurrentConnection();
        if (connection != null) {
            Box box = connection.getBox();
            for (BlockPos corner : box.corners()) {
                if (!connection.getMarkerPositions().contains(corner) && cache.hasLoadedOrUnloadedMarker(corner)) connection.addMarker(corner);
            }
        }
    }

    /** A mark placed on a corner of an existing box joins it. */
    public void onPlacedBy() {
        MarkerSubCache<VolumeConnection> cache = getLocalCache();
        for (BlockPos other : cache.getValidConnections(worldPosition)) {
            VolumeConnection connection = cache.getConnection(other);
            if (connection != null && connection.getBox().isCorner(worldPosition) && connection.addMarker(worldPosition)) break;
        }
    }

    @Override
    public BlockPos min() {
        VolumeConnection connection = getCurrentConnection();
        return connection == null ? worldPosition : connection.getBox().min();
    }

    @Override
    public BlockPos max() {
        VolumeConnection connection = getCurrentConnection();
        return connection == null ? worldPosition : connection.getBox().max();
    }

    /** Breaks every mark in this mark's box, dropping them. */
    @Override
    public void removeFromWorld() {
        if (level == null || level.isClientSide) return;
        VolumeConnection connection = getCurrentConnection();
        if (connection == null) return;
        for (BlockPos p : List.copyOf(connection.getMarkerPositions())) level.destroyBlock(p, true);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("showSignals", showSignals);
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        showSignals = tag.getBoolean("showSignals");
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        tag.putBoolean("showSignals", showSignals);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        showSignals = tag.getBoolean("showSignals");
    }
}
