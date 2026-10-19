package com.technovision.advancedgenetics.forge.client;

import com.technovision.advancedgenetics.AdvancedGeneticsClient;
import com.technovision.advancedgenetics.forge.ForgeNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Forge client hooks for the shared client registration. */
public final class AdvancedGeneticsForgeClient {

    private AdvancedGeneticsForgeClient() { }

    public static void init(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(AdvancedGeneticsClient::registerScreensAndProperties));
        modBus.addListener((RegisterKeyMappingsEvent event) -> AdvancedGeneticsClient.KEY_MAPPINGS.forEach(event::register));
        modBus.addListener((RegisterColorHandlersEvent.Item event) -> AdvancedGeneticsClient.registerItemColors(event::register));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                AdvancedGeneticsClient.tick(Minecraft.getInstance(), ForgeNetwork.CHANNEL::sendToServer);
            }
        });
    }
}
