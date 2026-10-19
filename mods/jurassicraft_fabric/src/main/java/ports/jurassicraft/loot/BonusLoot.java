package ports.jurassicraft.loot;

import java.util.Set;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import ports.jurassicraft.config.JurassicConfig;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.figure.ActionFigureItem;
import ports.jurassicraft.plants.PlantRegistry;

/** Add the original bonus pools to vanilla loot tables as Fabric loads them. */
public final class BonusLoot {
    private static final Set<String> CHESTS = Set.of("chests/village/village_weaponsmith",
            "chests/village/village_toolsmith", "chests/village/village_armorer", "chests/nether_bridge",
            "chests/simple_dungeon", "chests/stronghold_corridor", "chests/desert_pyramid", "chests/abandoned_mineshaft");
    private BonusLoot() {}
    static ItemStack actionFigure(Species species) { return ActionFigureItem.create(species); }

    public static void init() {
        LootTableEvents.MODIFY.register((resources, manager, id, table, source) -> addTo(id, table, JurassicConfig.LOOT_SPAWNRATE.get()));
    }

    static void addTo(ResourceLocation id, LootTable.Builder table, int percentage) {
        if (id.getNamespace().equals("minecraft")) {
            if (id.getPath().equals("gameplay/fishing")) {
                table.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                        .when(LootItemRandomChanceCondition.randomChance(.1F))
                        .add(LootItem.lootTableItem(PlantRegistry.GRACILARIA.get()).setWeight(25)));
            } else if (CHESTS.contains(id.getPath())) {
                float rate = Math.max(0, percentage) / 100F;
                int one = (int) rate, two = (int) (2 * rate), three = (int) (3 * rate);
                table.withPool(LootPool.lootPool().setRolls(UniformGenerator.between(1, 2))
                        .add(LootItem.lootTableItem(JurassicContent.PLANT_FOSSIL.get()).setWeight(5).apply(count(one, three)))
                        .add(LootItem.lootTableItem(JurassicContent.TWIG_FOSSIL.get()).setWeight(5).apply(count(one, three)))
                        .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.AMBER).setWeight(2).apply(count(0, one)))
                        .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.BONE).setWeight(2).apply(count(one, two))));
            }
            return;
        }
        // The JurassiCraft structure JSON already contains its bonus pools. Vanilla's
        // loot loader ignores Forge's pool names, so appending here would duplicate
        // them on every reload. Data packs may replace those tables normally.

    }

    private static LootItemConditionalFunction.Builder<?> count(int min, int max) {
        return SetItemCountFunction.setCount(UniformGenerator.between(min, max));
    }
}
