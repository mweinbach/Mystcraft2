package ports.jurassicraft.music;

import java.util.Set;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import ports.jurassicraft.config.JurassicConfig;

/** Add the original equally weighted record pool to the original chest categories. */
public final class MusicLoot {
    private static final Set<String> CHESTS = Set.of("chests/village/village_weaponsmith", "chests/village/village_toolsmith", "chests/village/village_armorer", "chests/nether_bridge", "chests/simple_dungeon", "chests/stronghold_corridor", "chests/desert_pyramid", "chests/abandoned_mineshaft");
    private MusicLoot() {}
    public static void register() {
        LootTableEvents.MODIFY.register((resources, manager, id, table, source) -> addTo(id, table, JurassicConfig.LOOT_SPAWNRATE.get()));
    }
    public static void addTo(ResourceLocation id, LootTable.Builder table, int percentage) {
        if (!id.getNamespace().equals("minecraft") || !CHESTS.contains(id.getPath())) return;
        int maxRolls = (int)(2 * (Math.max(0, percentage) / 100F));
        table.withPool(LootPool.lootPool().setRolls(UniformGenerator.between(0, maxRolls))
                .add(LootItem.lootTableItem(MusicRegistry.THEME.get()))
                .add(LootItem.lootTableItem(MusicRegistry.TROODONS.get()))
                .add(LootItem.lootTableItem(MusicRegistry.MUSCLE.get())));
    }
}
