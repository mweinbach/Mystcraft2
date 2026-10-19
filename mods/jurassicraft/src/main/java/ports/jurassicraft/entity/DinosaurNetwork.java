package ports.jurassicraft.entity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;

/** Dismount requests must come from the owner carrying the animal. */
public final class DinosaurNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PortMod.MOD_ID, "dinosaur_controls"), () -> VERSION, VERSION::equals, VERSION::equals);
    private DinosaurNetwork() {}
    static void register() {
        CHANNEL.messageBuilder(Dismount.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Dismount::write).decoder(Dismount::read)
                .consumerMainThread((message, supplier) -> {
                    ServerPlayer sender = supplier.get().getSender();
                    if (sender != null && sender.level.getEntity(message.entityId()) instanceof DinosaurEntity dinosaur)
                        dismount(sender, dinosaur);
                    supplier.get().setPacketHandled(true);
                }).add();
    }
    static boolean dismount(Player player, DinosaurEntity dinosaur) {
        if (dinosaur.species() != Species.MICRORAPTOR || dinosaur.getVehicle() != player
                || !player.getUUID().equals(dinosaur.getOwnerUUID())) return false;
        dinosaur.stopRiding();
        return true;
    }
    public static void sendDismount(DinosaurEntity dinosaur) { CHANNEL.sendToServer(new Dismount(dinosaur.getId())); }
    private record Dismount(int entityId) {
        void write(FriendlyByteBuf buffer) { buffer.writeVarInt(entityId); }
        static Dismount read(FriendlyByteBuf buffer) { return new Dismount(buffer.readVarInt()); }
    }
}
