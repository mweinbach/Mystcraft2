package ports.buildcraft.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import ports.buildcraft.core.marker.volume.VolumeBox;
import ports.buildcraft.core.marker.volume.VolumeBoxes;
import ports.buildcraft.transport.wire.WireSystems;

/** Upstream {@code BCCoreEventDist} and the wire tick of {@code BCTransportEventDist}; each loader forwards its level tick and entity join events here. */
public final class BCCoreEvents {
    private BCCoreEvents() {}

    /** Called at the end of each server level tick. */
    public static void onLevelTick(ServerLevel level) {
        VolumeBoxes.get(level).tick();
        WireSystems.get(level).tick();
    }

    /**
     * Called when a player joins a server level. Upstream delayed the message until the next server tick
     * so that it reaches single-player clients.
     */
    public static void onPlayerJoinLevel(ServerPlayer player, ServerLevel level) {
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (player.level == level && !player.isRemoved()) VolumeBoxes.get(level).sendTo(player);
        }));
        VolumeBoxes volumeBoxes = VolumeBoxes.get(level);
        for (VolumeBox volumeBox : volumeBoxes.volumeBoxes) {
            if (volumeBox.isPausedEditingBy(player)) volumeBox.resumeEditing();
        }
    }
}
