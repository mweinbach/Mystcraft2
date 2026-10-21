/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.network;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import ports.mekanism.platform.Platform;

public final class NetworkHooks {

    private NetworkHooks() {
    }

    public static void openScreen(ServerPlayer player, MenuProvider containerSupplier) {
        openScreen(player, containerSupplier, buf -> {
        });
    }

    public static void openScreen(ServerPlayer player, MenuProvider containerSupplier, BlockPos pos) {
        openScreen(player, containerSupplier, buf -> buf.writeBlockPos(pos));
    }

    public static void openScreen(ServerPlayer player, MenuProvider containerSupplier, Consumer<FriendlyByteBuf> extraDataWriter) {
        Platform.openMenu(player, containerSupplier, extraDataWriter);
    }

    public static Packet<?> getEntitySpawningPacket(Entity entity) {
        return Platform.entitySpawnPacket(entity);
    }
}
