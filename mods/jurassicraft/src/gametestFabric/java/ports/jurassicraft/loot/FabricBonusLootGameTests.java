package ports.jurassicraft.loot;

import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.loot.v2.LootTableSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.fabric.FabricMod;
import ports.jurassicraft.figure.ActionFigureItem;
import ports.jurassicraft.figure.ActionFigureRegistry;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.plants.PlantRegistry;

public final class FabricBonusLootGameTests implements FabricGameTest {
    private static void check(GameTestHelper helper, boolean condition, String message) { if (!condition) helper.fail(message); }
    private static LootContext context(GameTestHelper helper, long seed) {
        return new LootContext.Builder(helper.getLevel()).withRandom(RandomSource.create(seed))
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
    }

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
    private static LootTable table(GameTestHelper helper, String path) {
        ResourceLocation id = new ResourceLocation(PortMod.MOD_ID, path);
        check(helper, helper.getLevel().getServer().getLootTables().getIds().contains(id), "loot table missing: " + id);
        return helper.getLevel().getServer().getLootTables().get(id);
    }
    private static boolean bone(Item item) {
        return boneSpecies(item) != null;
    }
    private static Species boneSpecies(Item item) {
        for (Species species : Species.values()) for (var bone : JurassicContent.BONES.get(species).values())
            if (item == bone.get()) return species;
        return null;
    }
    private static boolean boneType(ItemStack stack, Species species, String type) {
        var bone = JurassicContent.BONES.get(species).get(type);
        return bone != null && stack.is(bone.get());
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public void copiedDigsiteHasExactlyOneBonusPool(GameTestHelper helper) {
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
    public void copiedStructureEntriesProduceValidDnaAndFigures(GameTestHelper helper) {
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
    public void vanillaNominalSkullSelectsRandomBone(GameTestHelper helper) {
        LootTable.Builder builder = LootTable.lootTable().setParamSet(LootContextParamSets.CHEST);
        BonusLoot.addTo(new ResourceLocation("minecraft", "chests/simple_dungeon"), FabricMod.lootSink(builder), 100);
        LootTable chest = builder.build();
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
    public void cryonicsLootResolvesToLaboratory(GameTestHelper helper) {
        CryonicsLootChecks.cryonicsResolvesToLaboratory(helper);
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public void vanillaLootInjectionPreservesRate(GameTestHelper helper) {
        LootTable.Builder chestBuilder = LootTable.lootTable().setParamSet(LootContextParamSets.CHEST);
        BonusLoot.addTo(new ResourceLocation("minecraft", "chests/simple_dungeon"), FabricMod.lootSink(chestBuilder), 0);
        LootTable noLoot = chestBuilder.build();
        for (int seed = 0; seed < 50; seed++) check(helper,
                noLoot.getRandomItems(context(helper, seed)).stream().allMatch(ItemStack::isEmpty),
                "zero-percent chest must produce no nonempty bonus items");
        LootTable.Builder fishingBuilder = LootTable.lootTable().setParamSet(LootContextParamSets.CHEST);
        BonusLoot.addTo(new ResourceLocation("minecraft", "gameplay/fishing"), FabricMod.lootSink(fishingBuilder), 100);
        LootTable fishing = fishingBuilder.build();
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
