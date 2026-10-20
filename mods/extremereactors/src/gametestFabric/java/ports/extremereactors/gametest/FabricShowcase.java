/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Registers the development-only {@link ShowcaseCommand} on Fabric.
 */
public final class FabricShowcase implements ModInitializer {

    @Override
    public void onInitialize() {

        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
                ShowcaseCommand.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(Showcase::tick);
    }
}
