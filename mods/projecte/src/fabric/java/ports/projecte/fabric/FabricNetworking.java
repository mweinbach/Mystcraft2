/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import ports.projecte.lib.entity.IEntityAdditionalSpawnData;

/**
 * The two vanilla packets Forge extends with mod data: opening a menu with extra data for its client-side
 * constructor, and spawning an entity with {@link IEntityAdditionalSpawnData}.
 */
public final class FabricNetworking {

    public static final ResourceLocation OPEN_MENU = new ResourceLocation("projecte", "fabric_open_menu");
    public static final ResourceLocation SPAWN_ENTITY = new ResourceLocation("projecte", "fabric_spawn_entity");
    /**
     * Forge's limit on a menu's extra data.
     */
    private static final int MAX_MENU_DATA = 32600;

    private FabricNetworking() {
    }

    static void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> extraDataWriter) {
        if (player.level.isClientSide) {
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        player.nextContainerCounter();
        int windowId = player.containerCounter;
        FriendlyByteBuf extraData = PacketByteBufs.create();
        extraDataWriter.accept(extraData);
        if (extraData.readableBytes() > MAX_MENU_DATA) {
            throw new IllegalArgumentException("Menu extra data is too large: " + extraData.readableBytes() + " bytes");
        }
        AbstractContainerMenu menu = provider.createMenu(windowId, player.getInventory(), player);
        if (menu == null) {
            return;
        }
        FriendlyByteBuf payload = PacketByteBufs.create();
        payload.writeResourceLocation(Registry.MENU.getKey(menu.getType()));
        payload.writeVarInt(windowId);
        payload.writeComponent(provider.getDisplayName());
        payload.writeVarInt(extraData.readableBytes());
        payload.writeBytes(extraData);
        ServerPlayNetworking.send(player, OPEN_MENU, payload);
        player.containerMenu = menu;
        player.initMenu(menu);
    }

    static Packet<?> entitySpawnPacket(Entity entity) {
        FriendlyByteBuf payload = PacketByteBufs.create();
        new ClientboundAddEntityPacket(entity).write(payload);
        if (entity instanceof IEntityAdditionalSpawnData spawnData) {
            spawnData.writeSpawnData(payload);
        }
        return ServerPlayNetworking.createS2CPacket(SPAWN_ENTITY, payload);
    }
}
