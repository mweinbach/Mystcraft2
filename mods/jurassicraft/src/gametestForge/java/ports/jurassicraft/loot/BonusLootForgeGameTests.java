package ports.jurassicraft.loot;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.figure.ActionFigureItem;
import ports.jurassicraft.figure.ActionFigureRegistry;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.plants.PlantSpecies;

/**
 * Forge-only loot checks that isolate the {@value BonusLoot#POOL} pool by name. Forge keeps loot
 * pool names (from a pool's JSON {@code name} and {@code LootPool.Builder.name}) and looks them up
 * with {@code LootTable.getPool}; vanilla and Fabric loot tables keep no pool names. The same
 * structure loot is rolled on both loaders through whole tables by {@link BonusLootGameTests}.
 */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BonusLootForgeGameTests {
    private BonusLootForgeGameTests() {}
    private static void check(GameTestHelper h, boolean valid, String message) { if (!valid) h.fail(message); }
    private static ResourceLocation id(String namespace, String path) { return new ResourceLocation(namespace, path); }
    /** Only the structure table's named bonus pool, as loaded from the datapack. */
    private static LootTable structurePool(GameTestHelper h, ResourceLocation id) {
        LootPool pool = h.getLevel().getServer().getLootTables().get(id).getPool(BonusLoot.POOL);
        LootTable table = LootTable.lootTable().setParamSet(LootContextParamSets.CHEST).build();
        if (pool != null) table.addPool(pool);
        return table;
    }
    private static LootContext context(GameTestHelper h, long seed) {
        return new LootContext.Builder(h.getLevel()).withRandom(RandomSource.create(seed))
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
    }
    private static List<ItemStack> drops(LootTable table, LootContext context) {
        return table.getRandomItems(context).stream().filter(stack -> !stack.isEmpty()).toList();
    }
    private static boolean bone(Item item) { return boneSpecies(item) != null; }
    private static Species boneSpecies(Item item) {
        for (Species species : Species.values()) for (var bone : JurassicContent.BONES.get(species).values())
            if (item == bone.get()) return species;
        return null;
    }
    private static boolean amber(ItemStack stack) {
        return stack.is(JurassicContent.AMBER.get()) || stack.is(JurassicContent.AMBER_APHID.get());
    }
    private static ResourceLocation visitor(String room) {
        return id(PortMod.MOD_ID, "structure/visitor_centre/" + room);
    }

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void visitorRoomsProduceOriginalVariants(GameTestHelper h) {
        LootTable storage = structurePool(h, visitor("ground_storage"));
        LootContext storageContext = context(h, 137);
        Set<Item> wool = new HashSet<>(), ambers = new HashSet<>();
        for (int i = 0; i < 300; i++) for (ItemStack stack : drops(storage, storageContext)) {
            if (amber(stack)) { ambers.add(stack.getItem()); check(h, stack.getCount() <= 3, "storage amber count"); }
            else { wool.add(stack.getItem()); check(h, stack.getCount() <= 64, "storage wool count"); }
        }
        check(h, wool.size() == 16 && ambers.size() == 2, "storage must include all wool and amber variants");

        LootTable laboratory = structurePool(h, visitor("laboratory"));
        LootContext labContext = context(h, 138);
        Set<Species> tissues = new HashSet<>(), dnaSpecies = new HashSet<>();
        Set<PlantSpecies> plants = new HashSet<>();
        boolean labAmber = false;
        for (int i = 0; i < 2000; i++) for (ItemStack stack : drops(laboratory, labContext)) {
            if (amber(stack)) { labAmber = true; check(h, stack.getCount() <= 5, "lab amber count"); continue; }
            boolean found = false;
            for (Species species : Species.values()) {
                if (stack.is(GeneticsRegistry.softTissue(species).get())) {
                    tissues.add(species); check(h, stack.getCount() <= 3, "animal tissue count"); found = true; break;
                }
                if (stack.is(GeneticsRegistry.dinosaurDna(species).get())) {
                    var dna = Genetics.read(stack.getTag());
                    check(h, dna.isPresent() && dna.get().kind() == DNAData.Kind.DINOSAUR && dna.get().dinosaur() == species
                            && dna.get().quality() >= 5 && dna.get().quality() <= 50 && dna.get().quality() % 5 == 0
                            && DNAData.validGenetics(dna.get().genetics()), "invalid lab DNA for " + species);
                    dnaSpecies.add(species); found = true; break;
                }
            }
            if (!found) for (PlantSpecies species : PlantSpecies.values()) if (species.isPrehistoric()
                    && stack.is(GeneticsRegistry.plantTissue(species).get())) {
                plants.add(species); check(h, stack.getCount() <= 3, "plant tissue count"); found = true; break;
            }
            check(h, found, "unexpected laboratory bonus " + stack);
        }
        check(h, labAmber && tissues.size() == Species.values().length && dnaSpecies.size() == Species.values().length
                && plants.size() == Arrays.stream(PlantSpecies.values()).filter(PlantSpecies::isPrehistoric).count(),
                "laboratory must sample every registered species");

        LootTable kitchen = structurePool(h, visitor("kitchen"));
        LootContext kitchenContext = context(h, 139);
        boolean bottle = false;
        for (int i = 0; i < 100; i++) for (ItemStack stack : drops(kitchen, kitchenContext)) {
            check(h, stack.is(Items.POTION) && stack.getCount() == 1 && PotionUtils.getPotion(stack) == Potions.WATER,
                    "kitchen bonus must be water bottles");
            bottle = true;
        }
        check(h, bottle, "kitchen must produce water bottles");
        h.succeed();
    }

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void diningFiguresAndDigsiteBones(GameTestHelper h) {
        Set<Species> figures = new HashSet<>();
        LootTable dining = structurePool(h, visitor("dining_hall"));
        LootContext diningContext = context(h, 1218);
        boolean fossil = false, amber = false, figure = false;
        Set<Species> fossilSpecies = new HashSet<>();
        Set<Item> fossilParts = new HashSet<>();
        boolean nonTooth = false;
        for (int i = 0; i < 300; i++) for (ItemStack stack : drops(dining, diningContext)) {
            if (stack.is(ActionFigureRegistry.ITEM.get())) {
                figure = true;
                figures.add(ActionFigureItem.species(stack));
                check(h, stack.getCount() == 1, "figure count");
            }
            else if (amber(stack)) { amber = true; check(h, stack.getCount() == 1, "dining amber count"); }
            else {
                Species species = boneSpecies(stack.getItem());
                check(h, species != null && stack.getCount() <= 2, "dining bone");
                fossil = true;
                fossilSpecies.add(species);
                fossilParts.add(stack.getItem());
                var tooth = JurassicContent.BONES.get(species).get("tooth");
                if (tooth == null || !stack.is(tooth.get())) nonTooth = true;
            }
        }
        check(h, fossil && amber && figure && figures.size() == Species.values().length,
                "dining room must sample figures for every dinosaur");
        check(h, fossilSpecies.size() == Species.values().length && fossilSpecies.contains(Species.GALLIMIMUS)
                && fossilParts.size() > 30 && nonTooth,
                "upstream replaces the nominal tooth with random bones, including species without a tooth");
        LootTable digsite = structurePool(h, id(PortMod.MOD_ID, "structure/fossil_digsite"));
        LootContext digsiteContext = context(h, 2237);
        for (int i = 0; i < 100; i++) {
            var result = drops(digsite, digsiteContext);
            check(h, result.size() >= 6 && result.size() <= 9, "digsite must roll 6–9 bones");
            for (ItemStack stack : result) check(h, bone(stack.getItem()) && stack.getCount() <= 2, "invalid digsite fossil");
        }
        h.succeed();
    }

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void actualTablesReceivePools(GameTestHelper h) {
        var tables = h.getLevel().getServer().getLootTables();
        for (ResourceLocation id : new ResourceLocation[] {id("minecraft", "gameplay/fishing"),
                id("minecraft", "chests/simple_dungeon"), visitor("ground_storage"), visitor("laboratory"),
                visitor("dining_hall"), visitor("kitchen"), id(PortMod.MOD_ID, "structure/fossil_digsite")})
            check(h, tables.getIds().contains(id) && tables.get(id).getPool(BonusLoot.POOL) != null,
                    "registered loot table did not receive bonus pool: " + id);
        h.succeed();
    }
}
