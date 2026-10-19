package ports.jurassicraft.platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

/**
 * Loader-independent play packets. Payloads are read on the receiving game thread from a private
 * copy, so handlers never observe a released network buffer; malformed payloads are dropped.
 */
public final class Network {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, ServerHandler> SERVER = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, ClientHandler> CLIENT = new ConcurrentHashMap<>();

    /** Loader transport: wraps a payload in its own packet type and delivers incoming payloads here. */
    public interface Transport {
        Packet<?> toClient(ResourceLocation id, FriendlyByteBuf payload);
        Packet<?> toServer(ResourceLocation id, FriendlyByteBuf payload);
        boolean canSend(ServerPlayer player, ResourceLocation id);
    }

    @FunctionalInterface public interface ServerHandler { void handle(ServerPlayer sender, FriendlyByteBuf payload); }
    @FunctionalInterface public interface ClientHandler { void handle(FriendlyByteBuf payload); }

    private Network() {}

    public static void registerServer(ResourceLocation id, ServerHandler handler) {
        if (SERVER.putIfAbsent(id, handler) != null) throw new IllegalStateException("Duplicate server packet " + id);
    }
    public static void registerClient(ResourceLocation id, ClientHandler handler) {
        if (CLIENT.putIfAbsent(id, handler) != null) throw new IllegalStateException("Duplicate client packet " + id);
    }
    public static Iterable<ResourceLocation> serverIds() { return SERVER.keySet(); }
    public static Iterable<ResourceLocation> clientIds() { return CLIENT.keySet(); }

    public static FriendlyByteBuf buffer(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        writer.accept(buffer);
        return buffer;
    }

    public static boolean canSend(ServerPlayer player, ResourceLocation id) {
        return Platform.backend().network().canSend(player, id);
    }
    public static void sendToPlayer(ServerPlayer player, ResourceLocation id, Consumer<FriendlyByteBuf> writer) {
        if (canSend(player, id)) player.connection.send(Platform.backend().network().toClient(id, buffer(writer)));
    }
    public static void sendToTracking(Entity entity, ResourceLocation id, Consumer<FriendlyByteBuf> writer) {
        if (!(entity.level instanceof ServerLevel level)) return;
        Packet<?> packet = Platform.backend().network().toClient(id, buffer(writer));
        level.getChunkSource().broadcast(entity, packet);
    }
    /** Client-side only; the caller must run on a connected client. */
    public static Packet<?> toServer(ResourceLocation id, Consumer<FriendlyByteBuf> writer) {
        return Platform.backend().network().toServer(id, buffer(writer));
    }

    /** Called by the transport on the server thread; releases the buffer whether or not it is handled. */
    public static void receiveOnServer(ResourceLocation id, ServerPlayer sender, FriendlyByteBuf payload) {
        try {
            ServerHandler handler = SERVER.get(id);
            if (handler == null || sender == null) return;
            handler.handle(sender, payload);
        } catch (RuntimeException malformed) {
            LOGGER.debug("Dropped malformed {} packet from {}", id, sender.getGameProfile().getName(), malformed);
        } finally {
            payload.release();
        }
    }
    /** Called by the transport on the client thread; releases the buffer whether or not it is handled. */
    public static void receiveOnClient(ResourceLocation id, FriendlyByteBuf payload) {
        try {
            ClientHandler handler = CLIENT.get(id);
            if (handler == null) return;
            handler.handle(payload);
        } catch (RuntimeException malformed) {
            LOGGER.debug("Dropped malformed {} packet", id, malformed);
        } finally {
            payload.release();
        }
    }
}
