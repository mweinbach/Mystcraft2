package mekanism.additions.common.network;

import mekanism.additions.common.MekanismAdditions;
import mekanism.additions.common.network.to_client.PacketVoiceSession;
import mekanism.common.network.BasePacketHandler;
import net.minecraftforge.network.simple.SimpleChannel;

public class AdditionsPacketHandler extends BasePacketHandler {

    private final SimpleChannel netHandler = createChannel(MekanismAdditions.rl(MekanismAdditions.MODID), MekanismAdditions.instance.versionNumber);

    @Override
    protected SimpleChannel getChannel() {
        return netHandler;
    }

    @Override
    public void initialize() {
        registerServerToClient(PacketVoiceSession.class, PacketVoiceSession::decode);
    }
}
