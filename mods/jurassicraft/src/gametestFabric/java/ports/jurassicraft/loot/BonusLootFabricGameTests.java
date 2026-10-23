package ports.jurassicraft.loot;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.loot.v2.LootTableSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import ports.jurassicraft.fabric.FabricMod;

/**
 * Fabric-only loot adapter check: Fabric's {@code LootTableEvents.MODIFY} also fires for tables a
 * datapack replaced and reports a {@link LootTableSource}, which the adapter must filter. Forge's
 * {@code LootTableLoadEvent} never fires for replaced tables, so Forge has no such branch. The bonus
 * loot itself is checked on both loaders by {@link BonusLootGameTests}.
 */
public final class BonusLootFabricGameTests implements FabricGameTest {
    private static void check(GameTestHelper helper, boolean condition, String message) { if (!condition) helper.fail(message); }

    /** Forge's LootTableLoadEvent skips tables a datapack replaced; the Fabric adapter must do the same. */
    @GameTest(template = "jurassicraft:cleaning_station_test")
    public void datapackReplacedLootTablesAreUntouched(GameTestHelper helper) {
        for (String id : new String[]{"minecraft:chests/simple_dungeon", "minecraft:gameplay/fishing"}) {
            for (LootTableSource source : LootTableSource.values()) {
                int[] pools = {0};
                LootTable.Builder table = new LootTable.Builder() {
                    @Override public LootTable.Builder withPool(LootPool.Builder pool) {
                        pools[0]++;
                        return super.withPool(pool);
                    }
                };
                FabricMod.modifyLootTable(new ResourceLocation(id), table, source);
                check(helper, (pools[0] > 0) == source.isBuiltin(),
                        id + " from " + source + (source.isBuiltin() ? " lost its bonus loot" : " was modified"));
            }
        }
        helper.succeed();
    }
}
