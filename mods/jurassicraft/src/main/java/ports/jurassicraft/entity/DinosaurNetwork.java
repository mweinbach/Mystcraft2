package ports.jurassicraft.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.platform.Network;
import ports.jurassicraft.platform.Reg;

/** Dismount requests are authorized against the sending player on the server thread. */
public final class DinosaurNetwork {
    public static final ResourceLocation DISMOUNT = Reg.id("dinosaur_dismount");
    private DinosaurNetwork() {}
    static void register() {
        Network.registerServer(DISMOUNT, (player, buffer) -> {
            int entityId = buffer.readVarInt();
            if (player.isAlive() && player.level.getEntity(entityId) instanceof DinosaurEntity dinosaur) dismount(player, dinosaur);
        });
    }
    static boolean dismount(Player player, DinosaurEntity dinosaur) {
        if (dinosaur.species() != Species.MICRORAPTOR || dinosaur.getVehicle() != player
                || !player.getUUID().equals(dinosaur.getOwnerUUID())) return false;
        dinosaur.stopRiding();
        return true;
    }
}
