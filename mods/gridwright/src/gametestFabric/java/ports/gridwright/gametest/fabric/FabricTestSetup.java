package ports.gridwright.gametest.fabric;

import net.fabricmc.api.ModInitializer;
import ports.gridwright.gametest.TestPlatform;

public class FabricTestSetup implements ModInitializer {

    @Override
    public void onInitialize() {
        TestPlatform.Holder.install(new FabricTestPlatform());
        FabricTestPlatform.registerEnergySink();
    }
}
