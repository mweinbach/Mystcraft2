package ports.buildcraft.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import ports.buildcraft.robotics.ZonePackets;
import ports.buildcraft.robotics.zone.ZonePlannerMapChunk;
import ports.buildcraft.robotics.zone.ZonePlannerMapChunkKey;
import ports.buildcraft.robotics.zone.ZonePlannerMapData;

/** Upstream {@code ZonePlannerMapDataClient}: a missing slice is requested once and filled in when the server answers. */
public final class ZonePlannerMapDataClient extends ZonePlannerMapData {
    public static final ZonePlannerMapDataClient INSTANCE = new ZonePlannerMapDataClient();
    /** A refused or delayed slice can be asked for again. The server only scans a few each tick. */
    private static final long RETRY_AFTER_NANOS = 1_000_000_000L;
    private final Map<ZonePlannerMapChunkKey, Long> pending = new HashMap<>();

    private ZonePlannerMapDataClient() {}

    @Override
    protected ZonePlannerMapChunk load(Level level, ZonePlannerMapChunkKey key) {
        long now = System.nanoTime();
        Long sent = pending.get(key);
        if (sent == null || now - sent >= RETRY_AFTER_NANOS) {
            pending.put(key, now);
            ClientPlatform.get().sendToServer(ZonePackets.MAP_REQUEST_CHANNEL, key::write);
        }
        return null;
    }

    public void receive(FriendlyByteBuf buffer) {
        ZonePlannerMapChunkKey key = ZonePlannerMapChunkKey.read(buffer);
        pending.remove(key);
        store(key, new ZonePlannerMapChunk(buffer));
    }
}
