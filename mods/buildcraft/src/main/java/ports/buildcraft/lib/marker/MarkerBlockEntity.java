package ports.buildcraft.lib.marker;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.lib.SyncedBlockEntity;

/**
 * Upstream {@code TileMarker}: registers itself in its level's marker sub-cache while loaded. Breaking
 * the block removes the marker; unloading its chunk only marks it unloaded, as upstream. Clients
 * receive the marker's connection with the block entity data, and only know the markers they have
 * loaded.
 */
public abstract class MarkerBlockEntity<C extends MarkerConnection<C>> extends SyncedBlockEntity {
    private boolean broken;

    protected MarkerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public abstract MarkerCache<? extends MarkerSubCache<C>> getCache();

    public MarkerSubCache<C> getLocalCache() {
        return getCache().getSubCache(level);
    }

    @Nullable
    public C getCurrentConnection() {
        return level == null ? null : getLocalCache().getConnection(worldPosition);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        getLocalCache().loadMarker(worldPosition);
    }

    /** Called by the block when it is broken or replaced, before the block entity is removed. */
    public void onBroken() {
        broken = true;
        getLocalCache().removeMarker(worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level == null || broken) return;
        if (level.isClientSide) getLocalCache().forgetMarker(worldPosition);
        else getLocalCache().unloadMarker(worldPosition);
    }

    /** Server side: sends the marker's new connection to clients. */
    void connectionChanged() {
        if (level != null && !isRemoved()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        C connection = getCurrentConnection();
        tag.put("connection", MarkerSubCache.writePositions(connection == null ? List.of() : connection.getMarkerPositions()));
    }

    @Override
    protected void readClient(CompoundTag tag) {
        getLocalCache().receiveConnection(worldPosition, MarkerSubCache.readPositions(tag.getList("connection", Tag.TAG_COMPOUND)));
    }

    @Override
    protected void loadServer(CompoundTag tag) {}
}
