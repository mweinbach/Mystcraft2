package ports.jurassicraft.music;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import ports.jurassicraft.JurassicEvents;

/** Add a named, evenly weighted 0–2 record pool to the original chest categories. */
public final class MusicLoot {
    public static final String POOL="jurassicraft_records";
    private static final Set<String> CHESTS=Set.of("chests/village/village_weaponsmith", "chests/village/village_toolsmith", "chests/village/village_armorer", "chests/nether_bridge", "chests/simple_dungeon", "chests/stronghold_corridor", "chests/desert_pyramid", "chests/abandoned_mineshaft");
    private MusicLoot() {}
    public static void addTo(ResourceLocation id, JurassicEvents.LootSink table, int percentage) {
        if (!id.getNamespace().equals("minecraft") || !CHESTS.contains(id.getPath())) return;
        int maxRolls = (int) (2 * (Math.max(0, percentage) / 100F));
        table.addPool(POOL, LootPool.lootPool().setRolls(UniformGenerator.between(0,maxRolls))
                .add(LootItem.lootTableItem(MusicRegistry.THEME.get()))
                .add(LootItem.lootTableItem(MusicRegistry.TROODONS.get()))
                .add(LootItem.lootTableItem(MusicRegistry.MUSCLE.get())));
    }
}
