package ports.jurassicraft.loot;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntSupplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.functions.SetPotionFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.plants.PlantRegistry;

/** Runtime pools from the 1.12.2 Loot.handleTable hook. */
@Mod.EventBusSubscriber(modid = PortMod.MOD_ID)
public final class BonusLoot {
    public static final String POOL = "jurassicraft_bonus";
    private static final Set<String> CHESTS = Set.of("chests/village/village_weaponsmith",
            "chests/village/village_toolsmith", "chests/village/village_armorer", "chests/nether_bridge",
            "chests/simple_dungeon", "chests/stronghold_corridor", "chests/desert_pyramid", "chests/abandoned_mineshaft");
    private static IntSupplier lootPercent = () -> 100;
    // Installed during mod construction after the action-figure registry is available.
    private static Function<Species, ItemStack> figureFactory = species -> ItemStack.EMPTY;

    private BonusLoot() {}

    /** Install the original chest loot percentage before loot tables load; changes require a data reload. */
    public static void setLootPercentSupplier(IntSupplier supplier) { lootPercent = Objects.requireNonNull(supplier); }

    /** Connect the action-figure item; the factory must return a fresh stack for the species. */
    public static void setActionFigureFactory(Function<Species, ItemStack> factory) { figureFactory = Objects.requireNonNull(factory); }

    static Function<Species, ItemStack> actionFigureFactory() { return figureFactory; }

    static ItemStack actionFigure(Species species) {
        ItemStack result = figureFactory.apply(species);
        return result == null ? ItemStack.EMPTY : result.copy();
    }

    @SubscribeEvent public static void tableLoaded(LootTableLoadEvent event) {
        addTo(event.getName(), event.getTable(), lootPercent.getAsInt());
    }

    /** Public for integration and isolated GameTests; percentage applies to vanilla chests only. */
    public static void addTo(ResourceLocation id, LootTable table, int percentage) {
        if (table.getPool(POOL) != null) return;
        if (id.getNamespace().equals("minecraft")) {
            if (id.getPath().equals("gameplay/fishing")) {
                table.addPool(LootPool.lootPool().name(POOL).setRolls(ConstantValue.exactly(1))
                        .when(LootItemRandomChanceCondition.randomChance(.1F))
                        .add(LootItem.lootTableItem(PlantRegistry.GRACILARIA.get()).setWeight(25)).build());
            } else if (CHESTS.contains(id.getPath())) {
                float rate = Math.max(0, percentage) / 100F;
                int one = (int) rate, two = (int) (2 * rate), three = (int) (3 * rate);
                table.addPool(LootPool.lootPool().name(POOL).setRolls(UniformGenerator.between(1, 2))
                        .add(LootItem.lootTableItem(JurassicContent.PLANT_FOSSIL.get()).setWeight(5)
                                .apply(count(one, three)))
                        .add(LootItem.lootTableItem(JurassicContent.TWIG_FOSSIL.get()).setWeight(5)
                                .apply(count(one, three)))
                        .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.AMBER).setWeight(2).apply(count(0, one)))
                        .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.BONE).setWeight(2).apply(count(one, two)))
                        .build());
            }
            return;
        }
        if (!id.getNamespace().equals(PortMod.MOD_ID)) return;
        switch (id.getPath()) {
            case "structure/visitor_centre/ground_storage" -> table.addPool(LootPool.lootPool().name(POOL)
                    .setRolls(UniformGenerator.between(5, 6))
                    .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.AMBER).apply(count(0, 3)))
                    .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.WOOL).apply(count(0, 64))).build());
            case "structure/visitor_centre/laboratory" -> table.addPool(LootPool.lootPool().name(POOL)
                    .setRolls(UniformGenerator.between(3, 4))
                    .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.DINOSAUR_DNA))
                    .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.ANIMAL_TISSUE).apply(count(0, 3)))
                    .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.PLANT_TISSUE).apply(count(0, 3)))
                    .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.AMBER).apply(count(0, 5))).build());
            case "structure/visitor_centre/dining_hall" -> table.addPool(LootPool.lootPool().name(POOL)
                    .setRolls(UniformGenerator.between(8, 11))
                    .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.AMBER).setWeight(2).apply(count(0, 1)))
                    .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.BONE).setWeight(2).apply(count(1, 2)))
                    .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.ACTION_FIGURE)).build());
            case "structure/visitor_centre/kitchen" -> table.addPool(LootPool.lootPool().name(POOL)
                    .setRolls(UniformGenerator.between(3, 4))
                    .add(LootItem.lootTableItem(Items.POTION).apply(count(0, 1))
                            .apply(SetPotionFunction.setPotion(Potions.WATER))).build());
            case "structure/fossil_digsite" -> table.addPool(LootPool.lootPool().name(POOL)
                    .setRolls(UniformGenerator.between(6, 9))
                    .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.BONE).apply(count(1, 1)))
                    .add(DynamicLootEntry.entry(DynamicLootEntry.Kind.BONE).setWeight(2).apply(count(1, 2))).build());
            default -> { }
        }
    }

    private static net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction.Builder<?> count(int min, int max) {
        return SetItemCountFunction.setCount(UniformGenerator.between(min, max));
    }
}
