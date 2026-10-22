package mekanism.additions.client;

import io.netty.channel.local.LocalAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;
import mekanism.additions.client.voice.VoiceClient;
import mekanism.common.Mekanism;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public class AdditionsClient {

    private AdditionsClient() {
    }

    private static VoiceClient voiceClient;
    private static UUID voiceSession;

    public static void reset() {
        voiceSession = null;
        if (voiceClient != null) {
            voiceClient.disconnect();
            voiceClient = null;
        }
    }

    public static void acceptVoiceSession(UUID token) {
        voiceSession = token;
        launch();
    }

    public static void launch() {
        if (voiceSession == null || voiceClient != null) {
            return;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        SocketAddress address = connection == null ? null : connection.getConnection().getRemoteAddress();
        //local connection
        if (address instanceof LocalAddress) {
            voiceClient = new VoiceClient("127.0.0.1", voiceSession);
            AdditionsClient.voiceClient.start();
            //remote connection
        } else if (address instanceof InetSocketAddress socketAddress) {
            voiceClient = new VoiceClient(socketAddress.getHostString(), voiceSession);
            AdditionsClient.voiceClient.start();
        } else {
            Mekanism.logger.error("Unknown connection address detected, voice client will not launch.");
        }
    }
}