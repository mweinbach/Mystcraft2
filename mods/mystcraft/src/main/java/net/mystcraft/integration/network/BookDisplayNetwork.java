package net.mystcraft.integration.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;
import net.mystcraft.integration.platform.GameplayPlatform;
import net.mystcraft.minecraft.exploration.BookDisplayMenu;
import net.mystcraft.minecraft.exploration.MystLecternBlockEntity;
import net.mystcraft.minecraft.entity.LinkBookEntity;

/** Native opening payload only; link activation still uses the validated LinkingNetwork session. */
public final class BookDisplayNetwork {
    private BookDisplayNetwork() {}
    public static void open(ServerPlayer player, MystLecternBlockEntity lectern) {
        GameplayPlatform.openMenu(player, new SimpleMenuProvider((id, inventory, owner) ->
                new BookDisplayMenu(id, inventory, lectern, lectern.getBlockPos(), null), Component.translatable("container.mystcraft.book_display")),
                data -> { data.writeBoolean(true); data.writeBlockPos(lectern.getBlockPos()); });
        if (lectern.getBook().getItem() instanceof net.mystcraft.minecraft.item.LinkingBookItem)
            LinkingNetwork.openLectern(player, lectern.getBlockPos());
    }
    public static void open(ServerPlayer player, LinkBookEntity entity) {
        GameplayPlatform.openMenu(player, new SimpleMenuProvider((id, inventory, owner) ->
                new BookDisplayMenu(id, inventory, entity.createBookWrapper(), null, entity), Component.translatable("container.mystcraft.book_display")), data -> data.writeBoolean(false));
        if (entity.getBook().getItem() instanceof net.mystcraft.minecraft.item.LinkingBookItem)
            LinkingNetwork.openEntity(player, entity);
    }
}
