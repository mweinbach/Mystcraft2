package ports.buildcraft.builders.snapshot;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.platform.Platform;

/**
 * Upstream {@code MessageSnapshotRequest} and {@code MessageSnapshotResponse}: a client asks the server for a
 * snapshot it holds a header of, to draw it, and gets it back as gzipped NBT. The same encoding carries
 * snapshots through the electronic library.
 */
public final class SnapshotPackets {
    public static final ResourceLocation REQUEST_CHANNEL = BuildCraft.id("snapshot_request");
    public static final ResourceLocation RESPONSE_CHANNEL = BuildCraft.id("snapshot_response");
    /** What reading one snapshot may allocate, so a client cannot upload a decompression bomb. */
    private static final long MAX_NBT_BYTES = 64L * 1024 * 1024;

    private SnapshotPackets() {}

    public static byte[] compress(Snapshot snapshot) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(Snapshot.writeToNBT(snapshot), out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public static Snapshot decompress(byte[] bytes) throws IOException, InvalidSnapshotException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes))))) {
            return Snapshot.readFromNBT(NbtIo.read(in, new NbtAccounter(MAX_NBT_BYTES)));
        }
    }

    public static void write(FriendlyByteBuf buffer, Snapshot snapshot) {
        buffer.writeByteArray(compress(snapshot));
    }

    public static Snapshot read(FriendlyByteBuf buffer) throws IOException, InvalidSnapshotException {
        return decompress(buffer.readByteArray());
    }

    /** Answers with the snapshot, or not at all when the server does not have it, as upstream. */
    public static void receiveRequest(ServerPlayer player, FriendlyByteBuf buffer) {
        CompoundTag keyTag = buffer.readNbt();
        if (keyTag == null) return;
        Snapshot snapshot = SnapshotStorage.get(SnapshotStorage.Side.SERVER).getSnapshot(new Snapshot.Key(keyTag));
        if (snapshot != null) Platform.get().sendToPlayer(player, RESPONSE_CHANNEL, payload -> write(payload, snapshot));
    }
}
