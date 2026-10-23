package net.mystcraft.fabric;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.mystcraft.minecraft.exploration.LegacyTreasureHooks;

/**
 * Fabric counterpart of the JSON hook in {@link LegacyTreasureHooks}: adds the same weight-10
 * empty entry to the main (first) pool of each targeted built-in chest table. Datapack
 * replacements are left alone.
 */
final class FabricLegacyTreasureLoot {
    private static final int EMPTY_WEIGHT = 10;

    private FabricLegacyTreasureLoot() {}

    static void initialize() {
        LootTableEvents.MODIFY.register((resources, loot, id, table, source) -> {
            if (!source.isBuiltin() || !LegacyTreasureHooks.TARGETS.contains(id)) return;
            int[] index = {0};
            table.modifyPools(pool -> {
                if (index[0]++ == 0) pool.add(EmptyLootItem.emptyItem().setWeight(EMPTY_WEIGHT));
            });
        });
    }
}
