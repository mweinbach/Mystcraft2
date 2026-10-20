package ports.buildcraft.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ports.buildcraft.builders.snapshot.InvalidSnapshotException;
import ports.buildcraft.builders.snapshot.Snapshot;
import ports.buildcraft.builders.snapshot.SnapshotPackets;

/** Upstream {@code ClientSnapshots}: snapshots fetched from the server to draw, each asked for once. */
public final class ClientSnapshots {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");
    private static final Map<Snapshot.Key, Snapshot> SNAPSHOTS = new HashMap<>();
    private static final Set<Snapshot.Key> PENDING = new HashSet<>();

    private ClientSnapshots() {}

    /** The snapshot if it has arrived; otherwise null, asking the server for it the first time. */
    @Nullable
    public static Snapshot get(Snapshot.Key key) {
        Snapshot found = SNAPSHOTS.get(key);
        if (found == null && PENDING.add(key)) {
            ClientPlatform.get().sendToServer(SnapshotPackets.REQUEST_CHANNEL, buffer -> buffer.writeNbt(key.serializeNBT()));
        }
        return found;
    }

    static void receive(FriendlyByteBuf buffer) {
        try {
            Snapshot snapshot = SnapshotPackets.read(buffer);
            PENDING.remove(snapshot.key);
            SNAPSHOTS.put(snapshot.key, snapshot);
        } catch (IOException | InvalidSnapshotException e) {
            LOGGER.error("Failed to read a snapshot sent to draw", e);
        }
    }
}
