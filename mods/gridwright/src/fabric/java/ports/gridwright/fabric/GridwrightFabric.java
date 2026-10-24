package ports.gridwright.fabric;

import ports.gridwright.Gridwright;
import ports.gridwright.platform.Platform;
import net.fabricmc.api.ModInitializer;

public class GridwrightFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricPlatform platform = new FabricPlatform();
        Platform.install(platform);
        Gridwright.init();
        platform.flush();
        FabricTransfer.register();
    }
}
