package ports.gridwright.forge.client;

import ports.gridwright.client.GridwrightClient;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Forge client hooks for the shared client registration. */
public final class GridwrightForgeClient {

    private GridwrightForgeClient() { }

    public static void init(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(GridwrightClient::registerScreens));
    }
}
