package ports.jurassicraft.decor.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import ports.jurassicraft.decor.ParkSignNetwork;
import ports.jurassicraft.decor.ParkSigns;

public final class ParkSignClientNetwork {
    private ParkSignClientNetwork() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ParkSignNetwork.OPEN, (client, handler, buffer, responseSender) -> {
            BlockPos pos = buffer.readBlockPos();
            int side = buffer.readUnsignedByte(), hand = buffer.readUnsignedByte();
            if (side > 5 || hand >= InteractionHand.values().length) return;
            Direction facing = Direction.from3DDataValue(side);
            if (!facing.getAxis().isHorizontal()) return;
            client.execute(() -> PaddockSignScreen.open(pos, facing, InteractionHand.values()[hand]));
        });
    }
    static void sendSelection(BlockPos pos, Direction side, InteractionHand hand, String artwork) {
        int index = ParkSigns.PADDOCKS.indexOf(artwork);
        if (index < 0) return;
        FriendlyByteBuf buffer = PacketByteBufs.create();
        buffer.writeBlockPos(pos).writeByte(side.get3DDataValue()).writeByte(hand.ordinal()).writeByte(index);
        ClientPlayNetworking.send(ParkSignNetwork.SELECT, buffer);
    }
}
