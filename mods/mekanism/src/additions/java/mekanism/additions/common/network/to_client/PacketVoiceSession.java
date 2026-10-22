package mekanism.additions.common.network.to_client;

import java.util.UUID;
import mekanism.additions.client.AdditionsClient;
import mekanism.common.network.IMekanismPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * The one-time voice-socket token for the receiving player. It travels on the game connection, which is already
 * authenticated, and the voice socket presents it instead of its IP address.
 */
public class PacketVoiceSession implements IMekanismPacket {

    private final UUID token;

    public PacketVoiceSession(UUID token) {
        this.token = token;
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        AdditionsClient.acceptVoiceSession(token);
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(token);
    }

    public static PacketVoiceSession decode(FriendlyByteBuf buffer) {
        return new PacketVoiceSession(buffer.readUUID());
    }
}
