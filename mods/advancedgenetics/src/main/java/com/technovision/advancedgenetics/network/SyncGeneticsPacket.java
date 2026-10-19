package com.technovision.advancedgenetics.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** Server-to-client gene sync; each loader's client adapter hands the tag to {@code ClientPacketHandler}. */
public record SyncGeneticsPacket(CompoundTag genetics) {

    public static void encode(SyncGeneticsPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.genetics);
    }

    public static SyncGeneticsPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new SyncGeneticsPacket(tag == null ? new CompoundTag() : tag);
    }
}
