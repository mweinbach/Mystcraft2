package net.mystcraft.smoke.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.mystcraft.smoke.SmokeCommand;
import net.mystcraft.smoke.SmokeRegionPreparation;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class FabricSmokeMod implements ModInitializer {
    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> SmokeCommand.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(SmokeRegionPreparation::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(SmokeRegionPreparation::stop);
    }
}
