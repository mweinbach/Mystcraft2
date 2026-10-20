/*
 * Extreme Reactors 1.19.2 port - Forge adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.forge.client;

import it.zerono.mods.extremereactors.ExtremeReactors;
import it.zerono.mods.extremereactors.proxy.ClientProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ports.extremereactors.corelib.internal.LibClient;
import ports.extremereactors.forge.ForgePlatform;
import ports.extremereactors.platform.ClientPlatform;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The physical client part of the Forge entrypoint. As in the original mod, the client proxy is created while
 * the mod is constructed and finishes its setup in the client setup event.
 */
public final class ForgeClientSetup {

    public static void initialize(final IEventBus modBus, final ForgePlatform platform) {

        ClientPlatform.install(new ForgeClientPlatform(modBus));
        platform.installClientSide(new ClientSide());
        LibClient.initialize();

        final ClientProxy proxy = new ClientProxy();

        ExtremeReactors.setProxy(proxy);

        modBus.addListener(EventPriority.NORMAL, false, FMLClientSetupEvent.class,
                event -> event.enqueueWork(proxy::onClientInit));
        modBus.addListener(EventPriority.NORMAL, false, RegisterClientReloadListenersEvent.class, event ->
                event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
                        platform.getClientReloadListeners().forEach(Runnable::run)));
    }

    private static final class ClientSide implements ForgePlatform.ClientSide {

        @Override
        public Optional<Level> level() {
            return Optional.ofNullable(Minecraft.getInstance().level);
        }

        @Override
        public Optional<Player> player() {
            return Optional.ofNullable(Minecraft.getInstance().player);
        }

        @Nullable
        @Override
        public RecipeManager recipeManager() {

            final ClientPacketListener connection = Minecraft.getInstance().getConnection();

            return null != connection ? connection.getRecipeManager() : null;
        }
    }

    private ForgeClientSetup() {
    }
}
