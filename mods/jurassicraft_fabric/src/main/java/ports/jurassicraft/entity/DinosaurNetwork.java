package ports.jurassicraft.entity;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;

/** Dismount requests are authorized against the sending player on the server thread. */
public final class DinosaurNetwork {
    public static final ResourceLocation DISMOUNT = new ResourceLocation(PortMod.MOD_ID, "dinosaur_dismount");
    private DinosaurNetwork() {}
    static void register() {
        ServerPlayNetworking.registerGlobalReceiver(DISMOUNT, (server, player, handler, buffer, responseSender) -> {
            int entityId = buffer.readVarInt();
            server.execute(() -> {
                if (player.isAlive() && player.level.getEntity(entityId) instanceof DinosaurEntity dinosaur)
                    dismount(player, dinosaur);
            });
        });
    }
    static boolean dismount(Player player, DinosaurEntity dinosaur) {
        if (dinosaur.species() != Species.MICRORAPTOR || dinosaur.getVehicle() != player
                || !player.getUUID().equals(dinosaur.getOwnerUUID())) return false;
        dinosaur.stopRiding();
        return true;
    }
}
