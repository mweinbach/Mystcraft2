package net.mystcraft.integration.network;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.Villager;
import net.mystcraft.integration.platform.GameplayPlatform;
import net.mystcraft.minecraft.exploration.ArchivistMenu;
import net.mystcraft.minecraft.exploration.ArchivistShop;

/** Opens the same server-owned shop on both loaders. */
public final class ArchivistNetwork {
    private ArchivistNetwork() {}

    public static void open(ServerPlayer player, Villager villager) {
        if (!ArchivistShop.canOpen(player, villager)) return;
        GameplayPlatform.openMenu(player, new SimpleMenuProvider(
                (id, inventory, owner) -> new ArchivistMenu(id, inventory, villager),
                Component.translatable("container.mystcraft.archivist")),
                data -> data.writeInt(villager.getId()));
        if (player.containerMenu instanceof ArchivistMenu menu && menu.villager() == villager) {
            villager.setTradingPlayer(player);
        }
    }
}
