package ports.jurassicraft.decor.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import ports.jurassicraft.decor.ParkSignNetwork;
import ports.jurassicraft.decor.ParkSigns;
import ports.jurassicraft.platform.Network;
import ports.jurassicraft.platform.client.ClientPlatform;

public final class ParkSignClientNetwork {
    private ParkSignClientNetwork() {}
    public static void register() {
        Network.registerClient(ParkSignNetwork.OPEN, buffer -> {
            BlockPos pos = buffer.readBlockPos();
            int side = buffer.readUnsignedByte(), hand = buffer.readUnsignedByte();
            if (side > 5 || hand >= InteractionHand.values().length) return;
            Direction facing = Direction.from3DDataValue(side);
            if (!facing.getAxis().isHorizontal()) return;
            PaddockSignScreen.open(pos, facing, InteractionHand.values()[hand]);
        });
    }
    static void sendSelection(BlockPos pos, Direction side, InteractionHand hand, String artwork) {
        int index = ParkSigns.PADDOCKS.indexOf(artwork);
        if (index < 0) return;
        ClientPlatform.sendToServer(ParkSignNetwork.SELECT,
                buffer -> buffer.writeBlockPos(pos).writeByte(side.get3DDataValue()).writeByte(hand.ordinal()).writeByte(index));
    }
}
