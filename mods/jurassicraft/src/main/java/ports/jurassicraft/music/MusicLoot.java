package ports.jurassicraft.music;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.config.JurassicConfig;

/** Add a named, evenly weighted 0–2 record pool to the original chest categories. */
@Mod.EventBusSubscriber(modid=PortMod.MOD_ID)
public final class MusicLoot {
    public static final String POOL="jurassicraft_records";
    private static final Set<String> CHESTS=Set.of("chests/village/village_weaponsmith", "chests/village/village_toolsmith", "chests/village/village_armorer", "chests/nether_bridge", "chests/simple_dungeon", "chests/stronghold_corridor", "chests/desert_pyramid", "chests/abandoned_mineshaft");
    private MusicLoot() {}
    @SubscribeEvent public static void tableLoaded(LootTableLoadEvent event) { addTo(event.getName(),event.getTable(),JurassicConfig.LOOT_SPAWNRATE.get()); }
    public static void addTo(ResourceLocation id,LootTable table) {
        addTo(id, table, 100);
    }
    public static void addTo(ResourceLocation id,LootTable table,int percentage) {
        if (!id.getNamespace().equals("minecraft") || !CHESTS.contains(id.getPath()) || table.getPool(POOL)!=null) return;
        int maxRolls = (int) (2 * (Math.max(0, percentage) / 100F));
        table.addPool(LootPool.lootPool().name(POOL).setRolls(UniformGenerator.between(0,maxRolls))
                .add(LootItem.lootTableItem(MusicRegistry.THEME.get()))
                .add(LootItem.lootTableItem(MusicRegistry.TROODONS.get()))
                .add(LootItem.lootTableItem(MusicRegistry.MUSCLE.get())).build());
    }
}
