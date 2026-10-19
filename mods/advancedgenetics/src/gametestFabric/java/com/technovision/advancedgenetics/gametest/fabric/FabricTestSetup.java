package com.technovision.advancedgenetics.gametest.fabric;

import com.technovision.advancedgenetics.gametest.TestPlatform;
import net.fabricmc.api.ModInitializer;

public class FabricTestSetup implements ModInitializer {

    @Override
    public void onInitialize() {
        TestPlatform.Holder.install(new FabricTestPlatform());
        FabricTestPlatform.registerDamageProbe();
    }
}
