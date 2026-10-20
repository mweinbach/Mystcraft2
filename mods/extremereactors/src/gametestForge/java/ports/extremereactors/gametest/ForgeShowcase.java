/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ports.extremereactors.platform.Platform;

/**
 * Registers the development-only {@link ShowcaseCommand} on Forge.
 */
@Mod.EventBusSubscriber(modid = Platform.MOD_ID)
public final class ForgeShowcase {

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        ShowcaseCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {

        if (TickEvent.Phase.END == event.phase) {
            Showcase.tick(event.getServer());
        }
    }

    private ForgeShowcase() {
    }
}
