package ports.jurassicraft.gametest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import ports.jurassicraft.genetics.LabExtensionGameTests;

public final class FabricGameTestSetup implements ModInitializer {
    @Override public void onInitialize() {
        LabExtensionGameTests.registerBlocks((id, block) -> Registry.register(Registry.BLOCK, id, block));
        LabExtensionGameTests.registerItems((id, item) -> Registry.register(Registry.ITEM, id, item));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> GameTestEntityGate.install());
    }
}
