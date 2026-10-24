package ports.gridwright.fabric.client;

import ports.gridwright.client.GridwrightClient;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client hooks for the shared client registration. */
public class GridwrightFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GridwrightClient.registerScreens();
    }
}
