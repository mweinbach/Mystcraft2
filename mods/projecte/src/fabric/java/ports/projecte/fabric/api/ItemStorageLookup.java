/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.api;

import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.resources.ResourceLocation;

/**
 * Item access to the item storages that items such as backpacks and pouches hold.
 * <p>
 * Fabric API 0.77, the version for 1.19.2, only has block access ({@code ItemStorage.SIDED}). Later Fabric API versions
 * add {@code ItemStorage.ITEM} under {@link #ID}, with the same storage and context classes as {@link #ITEM}, so
 * {@code ItemApiLookup.get} returns this instance to any mod that builds the lookup from that id. Query it through
 * {@link ContainerItemContext#find}. When ProjectE asks another mod's stack for Forge's item handler, it uses the
 * storage registered here. ProjectE does not register its own items on this lookup.
 */
public final class ItemStorageLookup {

    public static final ResourceLocation ID = new ResourceLocation("fabric", "item_storage");

    public static final ItemApiLookup<Storage<ItemVariant>, ContainerItemContext> ITEM = ItemApiLookup.get(ID, Storage.asClass(), ContainerItemContext.class);

    private ItemStorageLookup() {
    }
}
