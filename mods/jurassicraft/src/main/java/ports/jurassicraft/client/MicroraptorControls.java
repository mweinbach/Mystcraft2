package ports.jurassicraft.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.entity.DinosaurNetwork;
import ports.jurassicraft.platform.client.ClientPlatform;

public final class MicroraptorControls {
    private static KeyMapping dismount;
    private MicroraptorControls() {}
    static void keys() {
        dismount = ClientPlatform.keyMapping(ClientPlatform.Key.of("key.jurassicraft.microraptor_dismount", GLFW.GLFW_KEY_C));
    }
    static void tick(Minecraft client) {
        if (dismount == null) return;
        while (dismount.consumeClick()) {
            if (client.screen == null && client.player != null)
                for (var passenger : client.player.getPassengers())
                    if (passenger instanceof DinosaurEntity dinosaur && dinosaur.species() == Species.MICRORAPTOR)
                        ClientPlatform.sendToServer(DinosaurNetwork.DISMOUNT, buffer -> buffer.writeVarInt(dinosaur.getId()));
        }
    }
}
