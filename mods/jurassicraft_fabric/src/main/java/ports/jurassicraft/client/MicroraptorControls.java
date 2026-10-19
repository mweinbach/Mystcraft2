package ports.jurassicraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.entity.DinosaurNetwork;

public final class MicroraptorControls {
    private static final KeyMapping DISMOUNT = new KeyMapping("key.jurassicraft.microraptor_dismount",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.jurassicraft");
    private MicroraptorControls() {}
    static void init() {
        KeyBindingHelper.registerKeyBinding(DISMOUNT);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (DISMOUNT.consumeClick()) {
                if (client.screen == null && client.player != null)
                    for (var passenger : client.player.getPassengers())
                        if (passenger instanceof DinosaurEntity dinosaur && dinosaur.species() == Species.MICRORAPTOR) {
                            var buffer = PacketByteBufs.create();
                            buffer.writeVarInt(dinosaur.getId());
                            ClientPlayNetworking.send(DinosaurNetwork.DISMOUNT, buffer);
                        }
            }
        });
    }
}
