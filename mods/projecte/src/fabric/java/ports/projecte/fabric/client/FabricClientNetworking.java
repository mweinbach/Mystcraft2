/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.client;

import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import ports.projecte.fabric.FabricGameEvents;
import ports.projecte.fabric.FabricNetworking;
import ports.projecte.fabric.FabricPlatform;
import ports.projecte.lib.entity.IEntityAdditionalSpawnData;
import ports.projecte.lib.fml.config.ConfigTracker;
import ports.projecte.lib.network.IContainerFactory;
import ports.projecte.lib.network.NetworkDirection;
import ports.projecte.platform.Platform;

/**
 * The client half of the port's networking: the mod channels, menus opened with extra data, entities spawned with
 * extra data, and the server configs a dedicated server sends.
 */
final class FabricClientNetworking implements FabricPlatform.ClientSide {

    private FabricClientNetworking() {
    }

    static void install() {
        FabricPlatform.installClient(new FabricClientNetworking());
        ClientPlayNetworking.registerGlobalReceiver(FabricNetworking.OPEN_MENU, (client, handler, buf, responseSender) -> {
            ResourceLocation typeName = buf.readResourceLocation();
            int windowId = buf.readVarInt();
            Component title = buf.readComponent();
            FriendlyByteBuf extraData = new FriendlyByteBuf(buf.readBytes(buf.readVarInt()));
            client.execute(() -> {
                try {
                    openMenu(client, Registry.MENU.get(typeName), windowId, title, extraData);
                } finally {
                    extraData.release();
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(FabricNetworking.SPAWN_ENTITY, (client, handler, buf, responseSender) -> {
            ClientboundAddEntityPacket packet = new ClientboundAddEntityPacket(buf);
            FriendlyByteBuf spawnData = new FriendlyByteBuf(buf.readBytes(buf.readableBytes()));
            client.execute(() -> {
                try {
                    spawnEntity(handler, packet, spawnData);
                } finally {
                    spawnData.release();
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(FabricGameEvents.CONFIG_SYNC, (client, handler, buf, responseSender) -> {
            String fileName = buf.readUtf();
            byte[] contents = buf.readByteArray();
            client.execute(() -> ConfigTracker.INSTANCE.receiveSyncedConfig(fileName, contents));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ConfigTracker.INSTANCE::clearSyncedConfigs));
    }

    @Override
    public void registerReceiver(ResourceLocation channel, Platform.PacketReceiver receiver) {
        ClientPlayNetworking.registerGlobalReceiver(channel, (client, handler, buf, responseSender) ->
              receiver.receive(buf, NetworkDirection.PLAY_TO_CLIENT, null));
    }

    @Override
    public void sendToServer(ResourceLocation channel, FriendlyByteBuf payload) {
        ClientPlayNetworking.send(channel, payload);
    }

    @Override
    public CompletableFuture<Void> execute(Runnable runnable) {
        return Minecraft.getInstance().submit(runnable);
    }

    /**
     * Mirrors {@link MenuScreens#create}, but builds the menu with its extra data as Forge does.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void openMenu(Minecraft minecraft, MenuType<?> type, int windowId, Component title, FriendlyByteBuf extraData) {
        if (minecraft.player == null) {
            return;
        }
        MenuScreens.ScreenConstructor constructor = MenuScreens.getConstructor(type);
        if (constructor == null) {
            return;
        }
        AbstractContainerMenu menu = type.constructor instanceof IContainerFactory<?> factory ? factory.create(windowId, minecraft.player.getInventory(), extraData)
                                                                                                : type.create(windowId, minecraft.player.getInventory());
        minecraft.player.containerMenu = menu;
        minecraft.setScreen(constructor.create(menu, minecraft.player.getInventory(), title));
    }

    private static void spawnEntity(ClientPacketListener handler, ClientboundAddEntityPacket packet, FriendlyByteBuf spawnData) {
        handler.handleAddEntity(packet);
        if (handler.getLevel() != null) {
            Entity entity = handler.getLevel().getEntity(packet.getId());
            if (entity instanceof IEntityAdditionalSpawnData additional) {
                additional.readSpawnData(spawnData);
            }
        }
    }
}
