package ports.jurassicraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.entity.DinosaurNetwork;

public final class MicroraptorControls {
    private static final KeyMapping DISMOUNT = new KeyMapping("key.jurassicraft.microraptor_dismount", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.jurassicraft");
    private MicroraptorControls() {}
    @Mod.EventBusSubscriber(modid = PortMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(DISMOUNT); }
    }
    @Mod.EventBusSubscriber(modid = PortMod.MOD_ID, value = Dist.CLIENT)
    public static final class Controls {
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft client = Minecraft.getInstance();
            while (DISMOUNT.consumeClick()) {
                if (client.screen == null && client.player != null)
                    for (var passenger : client.player.getPassengers())
                        if (passenger instanceof DinosaurEntity dinosaur && dinosaur.species() == Species.MICRORAPTOR)
                            DinosaurNetwork.sendDismount(dinosaur);
            }
        }
    }
}
