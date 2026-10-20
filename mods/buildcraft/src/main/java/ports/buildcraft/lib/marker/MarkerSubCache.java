package ports.buildcraft.lib.marker;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

/**
 * Upstream {@code MarkerSubCache}: every marker of one type in one level, loaded or not, and the
 * connections between them. The server's copy is saved with the level. Clients build theirs from the
 * markers they have loaded, each of which carries its connection in its block entity data, instead of
 * upstream's marker messages.
 */
public abstract class MarkerSubCache<C extends MarkerConnection<C>> {
    public final boolean isServer;
    private final WeakReference<Level> level;
    private final Map<BlockPos, C> posToConnection = new LinkedHashMap<>();
    private final Map<C, Set<BlockPos>> connectionToPos = new LinkedHashMap<>();
    /** Every known marker, mapped to whether its block entity is loaded. */
    private final Map<BlockPos, Boolean> markers = new LinkedHashMap<>();

    protected MarkerSubCache(Level level) {
        this.isServer = !level.isClientSide;
        this.level = new WeakReference<>(level);
    }

    public boolean hasLoadedOrUnloadedMarker(BlockPos pos) {
        return markers.containsKey(pos);
    }

    /** The marker's block entity, or null if it is not loaded. */
    @Nullable
    public MarkerBlockEntity<C> getMarker(BlockPos pos) {
        Level world = level.get();
        if (world == null || !markers.getOrDefault(pos, false)) return null;
        return world.getBlockEntity(pos) instanceof MarkerBlockEntity<?> marker && marker.getLocalCache() == this ? cast(marker) : null;
    }

    @SuppressWarnings("unchecked")
    private MarkerBlockEntity<C> cast(MarkerBlockEntity<?> marker) {
        return (MarkerBlockEntity<C>) marker;
    }

    public void loadMarker(BlockPos pos) {
        markers.put(pos.immutable(), true);
    }

    public void unloadMarker(BlockPos pos) {
        markers.put(pos.immutable(), false);
    }

    /**
     * Client side: drops an unloaded or broken marker but keeps its connection, which the server corrects
     * through the markers still in it.
     */
    public void forgetMarker(BlockPos pos) {
        markers.remove(pos);
    }

    public void removeMarker(BlockPos pos) {
        markers.remove(pos);
        C connection = getConnection(pos);
        if (connection != null) {
            connection.removeMarker(pos);
            refreshConnection(connection);
        }
    }

    public List<BlockPos> getAllMarkers() {
        return List.copyOf(markers.keySet());
    }

    @Nullable
    public C getConnection(BlockPos pos) {
        return posToConnection.get(pos);
    }

    public void destroyConnection(@Nullable C connection) {
        if (connection == null) return;
        Set<BlockPos> set = connectionToPos.remove(connection);
        if (set != null) deinitConnection(set);
    }

    public void addConnection(C connection) {
        initConnection(connection, new HashSet<>(connection.getMarkerPositions()));
    }

    public void refreshConnection(C connection) {
        Set<BlockPos> lastSeen = connectionToPos.get(connection);
        if (lastSeen == null) {
            addConnection(connection);
            return;
        }
        Set<BlockPos> invalid = new HashSet<>(lastSeen);
        lastSeen = new HashSet<>(connection.getMarkerPositions());
        invalid.removeAll(lastSeen);
        deinitConnection(invalid);
        initConnection(connection, lastSeen);
        if (lastSeen.isEmpty()) connectionToPos.remove(connection);
    }

    private void deinitConnection(Set<BlockPos> set) {
        for (BlockPos p : set) posToConnection.remove(p);
        connectionChanged(set);
    }

    private void initConnection(C connection, Set<BlockPos> lastSeen) {
        if (lastSeen.size() < 2) {
            connectionToPos.remove(connection);
            for (BlockPos p : lastSeen) posToConnection.remove(p);
            connectionChanged(lastSeen);
            return;
        }
        connectionToPos.put(connection, lastSeen);
        for (BlockPos p : lastSeen) posToConnection.put(p, connection);
        connectionChanged(lastSeen);
    }

    /** Server markers whose connection changed send it to clients. */
    private void connectionChanged(Collection<BlockPos> positions) {
        if (!isServer) return;
        for (BlockPos p : positions) {
            MarkerBlockEntity<C> marker = getMarker(p);
            if (marker != null) marker.connectionChanged();
        }
    }

    public List<C> getConnections() {
        return List.copyOf(connectionToPos.keySet());
    }

    public abstract boolean tryConnect(BlockPos from, BlockPos to);

    /** Checks if {@link #tryConnect(BlockPos, BlockPos)} would succeed at this time. */
    public abstract boolean canConnect(BlockPos from, BlockPos to);

    public abstract List<BlockPos> getValidConnections(BlockPos from);

    /** Rebuilds a connection from its saved or synced positions. */
    protected abstract C createConnection(List<BlockPos> positions);

    /** Client side: applies the connection that the server sent with the marker at {@code self}. */
    public void receiveConnection(BlockPos self, List<BlockPos> positions) {
        C existing = getConnection(self);
        if (positions.size() < 2) {
            if (existing != null) {
                existing.removeMarker(self);
                refreshConnection(existing);
            }
            return;
        }
        if (existing != null && List.copyOf(existing.getMarkerPositions()).equals(positions)) return;
        for (BlockPos p : positions) destroyConnection(getConnection(p));
        addConnection(createConnection(positions));
    }

    /** Upstream {@code MarkerSavedData.writeToNBT}. */
    public CompoundTag save(CompoundTag tag) {
        ListTag positions = new ListTag();
        for (BlockPos p : markers.keySet()) positions.add(NbtUtils.writeBlockPos(p));
        tag.put("positions", positions);
        ListTag connections = new ListTag();
        for (C connection : connectionToPos.keySet()) connections.add(writePositions(connection.getMarkerPositions()));
        tag.put("connections", connections);
        return tag;
    }

    /** Upstream {@code loadInto}: saved markers start unloaded until their block entities load. */
    public void load(CompoundTag tag) {
        for (Tag p : tag.getList("positions", Tag.TAG_COMPOUND)) markers.putIfAbsent(NbtUtils.readBlockPos((CompoundTag) p), false);
        for (Tag connection : tag.getList("connections", Tag.TAG_LIST)) addConnection(createConnection(readPositions((ListTag) connection)));
    }

    public static ListTag writePositions(Collection<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos p : positions) list.add(NbtUtils.writeBlockPos(p));
        return list;
    }

    public static List<BlockPos> readPositions(ListTag list) {
        List<BlockPos> positions = new ArrayList<>();
        for (Tag p : list) positions.add(NbtUtils.readBlockPos((CompoundTag) p));
        return positions;
    }
}
