package net.mystcraft.fabric.exploration;

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.mystcraft.integration.registry.ExplorationRegistry;

public final class FabricLecternInventory {
    private FabricLecternInventory() {}
    public static void initialize() {
        ItemStorage.SIDED.registerForBlockEntity((lectern, direction) -> InventoryStorage.of(lectern, direction), ExplorationRegistry.LECTERN_ENTITY.get());
    }
}
