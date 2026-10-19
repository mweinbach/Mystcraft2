package com.technovision.advancedgenetics.network;

import com.technovision.advancedgenetics.client.ClientPacketHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncGeneticsPacket(CompoundTag genetics) {

    public static void encode(SyncGeneticsPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.genetics);
    }

    public static SyncGeneticsPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new SyncGeneticsPacket(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(SyncGeneticsPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleGeneticsSync(packet.genetics));
        context.get().setPacketHandled(true);
    }
}
