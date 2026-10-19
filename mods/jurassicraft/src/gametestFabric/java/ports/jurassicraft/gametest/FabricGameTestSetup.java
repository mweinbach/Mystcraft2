package ports.jurassicraft.gametest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class FabricGameTestSetup implements ModInitializer {
    @Override public void onInitialize() { ServerLifecycleEvents.SERVER_STARTED.register(server -> GameTestEntityGate.install()); }
}
