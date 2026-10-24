package ports.gridwright.gametest.fabric;

import net.fabricmc.api.ModInitializer;
import ports.gridwright.gametest.TestPlatform;
import ports.gridwright.gametest.TransferTestPlatform;

public class FabricTestSetup implements ModInitializer {

    @Override
    public void onInitialize() {
        TestPlatform.Holder.install(new FabricTestPlatform());
        FabricTestPlatform.registerEnergySink();
        FabricTestPlatform.registerClaims();
        TransferTestPlatform.Holder.install(new FabricTransferTestPlatform());
        FabricTransferTestPlatform.register();
    }
}
