package ports.jurassicraft.platform;

import java.util.concurrent.atomic.AtomicInteger;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Runnable without a Minecraft server; every received payload must be released, handled or not. */
public final class NetworkLogicTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static FriendlyByteBuf payload() { return new FriendlyByteBuf(Unpooled.buffer().writeInt(7)); }

    public static void main(String[] args) {
        ResourceLocation unknown = new ResourceLocation("jurassicraft", "test_unknown");
        ResourceLocation serverbound = new ResourceLocation("jurassicraft", "test_serverbound");
        ResourceLocation clientbound = new ResourceLocation("jurassicraft", "test_clientbound");
        ResourceLocation malformed = new ResourceLocation("jurassicraft", "test_malformed");
        AtomicInteger serverCalls = new AtomicInteger(), clientCalls = new AtomicInteger();
        Network.registerServer(serverbound, (sender, buffer) -> serverCalls.incrementAndGet());
        Network.registerClient(clientbound, buffer -> clientCalls.addAndGet(buffer.readInt()));
        Network.registerClient(malformed, buffer -> buffer.readLong());

        FriendlyByteBuf buffer = payload();
        Network.receiveOnServer(unknown, null, buffer);
        check(buffer.refCnt() == 0, "unknown serverbound packet released");

        buffer = payload();
        Network.receiveOnServer(serverbound, null, buffer);
        check(buffer.refCnt() == 0 && serverCalls.get() == 0, "serverbound packet without a sender released unhandled");

        buffer = payload();
        Network.receiveOnClient(unknown, buffer);
        check(buffer.refCnt() == 0, "unknown clientbound packet released");

        buffer = payload();
        Network.receiveOnClient(malformed, buffer);
        check(buffer.refCnt() == 0, "malformed clientbound packet released");

        buffer = payload();
        Network.receiveOnClient(clientbound, buffer);
        check(buffer.refCnt() == 0 && clientCalls.get() == 7, "handled clientbound packet released");

        System.out.println("Network payload release passed");
    }
}
