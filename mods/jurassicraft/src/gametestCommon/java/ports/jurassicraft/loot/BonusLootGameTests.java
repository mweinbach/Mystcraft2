package ports.jurassicraft.loot;

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
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.figure.ActionFigureItem;
import ports.jurassicraft.figure.ActionFigureRegistry;
import ports.jurassicraft.gametest.TestPlatform;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.plants.PlantRegistry;

public final class BonusLootGameTests {
    private BonusLootGameTests() {}
    private static void check(GameTestHelper h, boolean valid, String message) { if (!valid) h.fail(message); }
    private static ResourceLocation id(String namespace, String path) { return new ResourceLocation(namespace, path); }
    /** The pools this loader's loot hook adds to {@code id} at {@code percent}, on an empty chest table. */
    private static LootTable isolated(GameTestHelper h, ResourceLocation id, int percent) {
        LootTable table = TestPlatform.get().loot(sink -> BonusLoot.addTo(id, sink, percent));
        check(h, table != null, "missing fossil pool for " + id);
        return table;
    }
    private static LootTable table(GameTestHelper helper, String path) {
        ResourceLocation id = new ResourceLocation(PortMod.MOD_ID, path);
        check(helper, helper.getLevel().getServer().getLootTables().getIds().contains(id), "loot table missing: " + id);
        return helper.getLevel().getServer().getLootTables().get(id);
    }
    private static boolean boneType(ItemStack stack, Species species, String type) {
        var bone = JurassicContent.BONES.get(species).get(type);
        return bone != null && stack.is(bone.get());
    }
    private static LootContext context(GameTestHelper h, long seed) {
        return new LootContext.Builder(h.getLevel()).withRandom(RandomSource.create(seed))
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
    }
    private static List<ItemStack> drops(LootTable table, LootContext context) {
        return table.getRandomItems(context).stream().filter(stack -> !stack.isEmpty()).toList();
    }
    private static boolean bone(Item item) {
        for (Species species : Species.values()) for (var bone : JurassicContent.BONES.get(species).values())
            if (item == bone.get()) return true;
        return false;
    }
    private static Species boneSpecies(Item item) {
        for (Species species : Species.values()) for (var bone : JurassicContent.BONES.get(species).values())
            if (item == bone.get()) return species;
        return null;
    }
    private static boolean amber(ItemStack stack) {
        return stack.is(JurassicContent.AMBER.get()) || stack.is(JurassicContent.AMBER_APHID.get());
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void fishingAndVanillaChests(GameTestHelper h) {
        LootTable fishing = isolated(h, id("minecraft", "gameplay/fishing"), 100);
        LootContext fishContext = context(h, 7190);
        int fishCount = 0;
        for (int i = 0; i < 2000; i++) {
            var result = drops(fishing, fishContext);
            check(h, result.size() <= 1, "fishing injection added duplicate pools");
            if (!result.isEmpty()) {
                check(h, result.get(0).is(PlantRegistry.GRACILARIA.get()) && result.get(0).getCount() == 1,
                        "unexpected fishing bonus");
                fishCount++;
            }
        }
        check(h, fishCount > 150 && fishCount < 250, "fishing bonus must be about 10%, got " + fishCount);

        for (String path : new String[] {"chests/village/village_weaponsmith", "chests/village/village_toolsmith",
                "chests/village/village_armorer", "chests/nether_bridge", "chests/simple_dungeon",
                "chests/stronghold_corridor", "chests/desert_pyramid", "chests/abandoned_mineshaft"})
            isolated(h, id("minecraft", path), 100);
        check(h, TestPlatform.get().loot(sink -> BonusLoot.addTo(id("minecraft", "chests/end_city_treasure"), sink, 100)) == null,
                "unrelated tables must not receive fossils");

        LootTable zero = isolated(h, id("minecraft", "chests/simple_dungeon"), 0);
        LootContext zeroContext = context(h, 119);
        for (int i = 0; i < 100; i++) check(h, drops(zero, zeroContext).isEmpty(), "zero-percent chest produced loot");
        LootTable chest = isolated(h, id("minecraft", "chests/simple_dungeon"), 100);
        LootContext chestContext = context(h, 718);
        boolean plant = false, twig = false, fossil = false, amber = false;
        Set<Species> fossilSpecies = new HashSet<>();
        Set<Item> fossilParts = new HashSet<>();
        boolean nonSkull = false;
        for (int i = 0; i < 1000; i++) for (ItemStack stack : drops(chest, chestContext)) {
            if (stack.is(JurassicContent.PLANT_FOSSIL.get())) { plant = true; check(h, stack.getCount() <= 3, "plant count"); }
            else if (stack.is(JurassicContent.TWIG_FOSSIL.get())) { twig = true; check(h, stack.getCount() <= 3, "twig count"); }
            else if (amber(stack)) { amber = true; check(h, stack.getCount() == 1, "amber count"); }
            else {
                Species species = boneSpecies(stack.getItem());
                check(h, species != null && stack.getCount() <= 2, "unexpected fossil pool item");
                fossil = true;
                fossilSpecies.add(species);
                fossilParts.add(stack.getItem());
                if (!stack.is(JurassicContent.BONES.get(species).get("skull").get())) nonSkull = true;
            }
        }
        check(h, plant && twig && fossil && amber, "all vanilla chest bonus types must occur");
        check(h, fossilSpecies.size() == Species.values().length && fossilParts.size() > 30 && nonSkull,
                "upstream replaces the nominal skull with a random species-appropriate bone");
        h.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void cryonicsLootResolvesToLaboratory(GameTestHelper h) {
        CryonicsLootChecks.cryonicsResolvesToLaboratory(h);
        h.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void copiedDigsiteHasExactlyOneBonusPool(GameTestHelper helper) {
        LootTable digsite = table(helper, "structure/fossil_digsite");
        LootContext digContext = context(helper, 2237);
        for (int seed = 0; seed < 100; seed++) {
            int bones = 0;
            for (ItemStack stack : digsite.getRandomItems(digContext)) {
                if (bone(stack.getItem())) {
                    bones++;
                    check(helper, stack.getCount() >= 1 && stack.getCount() <= 2, "invalid digsite bone count");
                }
            }
            check(helper, bones >= 6 && bones <= 9, "copied digsite must roll its 6–9 bone pool exactly once: " + bones);
        }
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void copiedStructureEntriesProduceValidDnaAndFigures(GameTestHelper helper) {
        LootTable lab = table(helper, "structure/visitor_centre/laboratory");
        LootTable dining = table(helper, "structure/visitor_centre/dining_hall");
        Set<Species> dnaSpecies = new HashSet<>(), figureSpecies = new HashSet<>(), boneSpecies = new HashSet<>();
        boolean nonTooth = false;
        LootContext labContext = context(helper, 138), diningContext = context(helper, 1218);
        for (int seed = 0; seed < 600; seed++) {
            for (ItemStack stack : lab.getRandomItems(labContext)) {
                for (Species species : Species.values()) if (stack.is(GeneticsRegistry.dinosaurDna(species).get())) {
                    var dna = Genetics.read(stack.getTag());
                    check(helper, dna.isPresent() && dna.get().kind() == DNAData.Kind.DINOSAUR
                            && dna.get().dinosaur() == species && dna.get().quality() >= 5 && dna.get().quality() <= 50
                            && dna.get().quality() % 5 == 0 && DNAData.validGenetics(dna.get().genetics()),
                            "laboratory DNA must match its item and carry a valid sequence");
                    dnaSpecies.add(species);
                }
            }
            for (ItemStack stack : dining.getRandomItems(diningContext)) {
                if (stack.is(ActionFigureRegistry.ITEM.get())) figureSpecies.add(ActionFigureItem.species(stack));
                Species species = boneSpecies(stack.getItem());
                if (species != null) {
                    boneSpecies.add(species);
                    if (!boneType(stack, species, "tooth") && !boneType(stack, species, "teeth")) nonTooth = true;
                }
            }
        }
        check(helper, dnaSpecies.size() == Species.values().length, "lab must produce DNA for every dinosaur");
        check(helper, figureSpecies.size() == Species.values().length, "dining hall must produce every action figure");
        check(helper, boneSpecies.size() == Species.values().length && boneSpecies.contains(Species.GALLIMIMUS) && nonTooth,
                "upstream replaces the nominal tooth with a random species bone, even for toothless dinosaurs");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void vanillaNominalSkullSelectsRandomBone(GameTestHelper helper) {
        LootTable chest = isolated(helper, new ResourceLocation("minecraft", "chests/simple_dungeon"), 100);
        LootContext context = context(helper, 718);
        Set<Species> speciesSeen = new HashSet<>();
        Set<Item> bones = new HashSet<>();
        boolean nonSkull = false;
        for (int i = 0; i < 1000; i++) for (ItemStack stack : chest.getRandomItems(context)) {
            Species species = boneSpecies(stack.getItem());
            if (species != null) {
                check(helper, stack.getCount() >= 1 && stack.getCount() <= 2, "invalid chest fossil count");
                speciesSeen.add(species);
                bones.add(stack.getItem());
                if (!boneType(stack, species, "skull")) nonSkull = true;
            }
        }
        check(helper, speciesSeen.size() == Species.values().length && bones.size() > 30 && nonSkull,
                "upstream replaces the nominal skull with a random species-appropriate bone");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void vanillaLootInjectionPreservesRate(GameTestHelper helper) {
        LootTable noLoot = isolated(helper, new ResourceLocation("minecraft", "chests/simple_dungeon"), 0);
        for (int seed = 0; seed < 50; seed++) check(helper,
                noLoot.getRandomItems(context(helper, seed)).stream().allMatch(ItemStack::isEmpty),
                "zero-percent chest must produce no nonempty bonus items");
        LootTable fishing = isolated(helper, new ResourceLocation("minecraft", "gameplay/fishing"), 100);
        int count = 0;
        LootContext fishContext = context(helper, 7190);
        for (int seed = 0; seed < 2000; seed++) {
            var result = fishing.getRandomItems(fishContext);
            check(helper, result.size() <= 1, "fishing pool must not be duplicated");
            if (!result.isEmpty()) {
                check(helper, result.get(0).is(PlantRegistry.GRACILARIA.get()), "unexpected fishing bonus");
                count++;
            }
        }
        check(helper, count > 130 && count < 270, "fishing bonus must be about ten percent: " + count);
        helper.succeed();
    }
}
