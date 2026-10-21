package ports.buildcraft.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import ports.buildcraft.robotics.ZonePackets;
import ports.buildcraft.robotics.zone.ZonePlannerMapChunk;
import ports.buildcraft.robotics.zone.ZonePlannerMapChunkKey;
import ports.buildcraft.robotics.zone.ZonePlannerMapData;

/** Upstream {@code ZonePlannerMapDataClient}: a missing slice is requested once and filled in when the server answers. */
public final class ZonePlannerMapDataClient extends ZonePlannerMapData {
    public static final ZonePlannerMapDataClient INSTANCE = new ZonePlannerMapDataClient();
    private final List<ZonePlannerMapChunkKey> pending = new ArrayList<>();

    private ZonePlannerMapDataClient() {}

    @Override
    protected ZonePlannerMapChunk load(Level level, ZonePlannerMapChunkKey key) {
        if (!pending.contains(key)) {
            pending.add(key);
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
