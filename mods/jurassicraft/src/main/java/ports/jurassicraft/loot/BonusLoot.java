package ports.jurassicraft.loot;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import ports.jurassicraft.JurassicEvents;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.figure.ActionFigureItem;
import ports.jurassicraft.plants.PlantRegistry;

/**
 * Runtime pools from the 1.12.2 Loot.handleTable hook. JurassiCraft's own structure
 * tables already contain their {@value #POOL} pools in JSON, so only vanilla tables
 * are extended here.
 */
public final class BonusLoot {
    public static final String POOL = "jurassicraft_bonus";
    private static final Set<String> CHESTS = Set.of("chests/village/village_weaponsmith",
            "chests/village/village_toolsmith", "chests/village/village_armorer", "chests/nether_bridge",
            "chests/simple_dungeon", "chests/stronghold_corridor", "chests/desert_pyramid", "chests/abandoned_mineshaft");

    private BonusLoot() {}

    static ItemStack actionFigure(Species species) { return ActionFigureItem.create(species); }

    /** The percentage scales vanilla chest counts only; changes apply on the next data reload. */
    public static void addTo(ResourceLocation id, JurassicEvents.LootSink table, int percentage) {
        if (!id.getNamespace().equals("minecraft")) return;
        if (id.getPath().equals("gameplay/fishing")) {
            table.addPool(POOL, LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                    .when(LootItemRandomChanceCondition.randomChance(.1F))
                    .add(LootItem.lootTableItem(PlantRegistry.GRACILARIA.get()).setWeight(25)));
        } else if (CHESTS.contains(id.getPath())) {
            float rate = Math.max(0, percentage) / 100F;
            int one = (int) rate, two = (int) (2 * rate), three = (int) (3 * rate);
            table.addPool(POOL, LootPool.lootPool().setRolls(UniformGenerator.between(1, 2))
                    .add(LootItem.lootTableItem(JurassicContent.PLANT_FOSSIL.get()).setWeight(5).apply(count(one, three)))
                    .add(LootItem.lootTableItem(JurassicContent.TWIG_FOSSIL.get()).setWeight(5).apply(count(one, three)))
                    .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.AMBER).setWeight(2).apply(count(0, one)))
                    .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.BONE).setWeight(2).apply(count(one, two))));
        }
    }

    private static LootItemConditionalFunction.Builder<?> count(int min, int max) {
        return SetItemCountFunction.setCount(UniformGenerator.between(min, max));
    }
}
