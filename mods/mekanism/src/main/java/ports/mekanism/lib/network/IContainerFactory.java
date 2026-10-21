/*
 * Mekanism 1.19.2 port - loader-neutral mirror of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

/**
 * Creates a menu on the client from the extra data the server sent when it opened the screen.
 */
@FunctionalInterface
public interface IContainerFactory<T extends AbstractContainerMenu> extends MenuType.MenuSupplier<T> {

    T create(int windowId, Inventory inv, @Nullable FriendlyByteBuf data);

    @Override
    default T create(int windowId, Inventory inv) {
        return create(windowId, inv, null);
    }
}
