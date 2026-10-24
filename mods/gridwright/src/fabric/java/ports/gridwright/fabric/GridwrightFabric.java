package ports.gridwright.fabric;

import ports.gridwright.Gridwright;
import ports.gridwright.platform.Platform;
import ports.gridwright.transfer.TransferPlatform;
import net.fabricmc.api.ModInitializer;

public class GridwrightFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricPlatform platform = new FabricPlatform();
        Platform.install(platform);
        TransferPlatform.install(new FabricTransferPlatform());
        Gridwright.init();
        platform.flush();
        FabricTransfer.register();
        FabricUtilityEvents.register();
    }
}
