package ports.jurassicraft.jei;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import ports.jurassicraft.assembly.AssemblyRegistry;
import ports.jurassicraft.assembly.SkeletonDisplayItem;
import ports.jurassicraft.assembly.SkeletonRecipes;
import ports.jurassicraft.bugs.BugFoods;
import ports.jurassicraft.bugs.BugRegistry;
import ports.jurassicraft.cleaning.CleaningStationRegistry;
import ports.jurassicraft.components.ComponentsRegistry;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.cultivator.CultivatorNutrition;
import ports.jurassicraft.cultivator.CultivatorRegistry;
import ports.jurassicraft.egg.FossilNestRegistry;
import ports.jurassicraft.egg.FossilizedEggRegistry;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.figure.ActionFigureCycleRecipe;
import ports.jurassicraft.figure.ActionFigureItem;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.grinder.FossilGrinderRecipes;
import ports.jurassicraft.grinder.FossilGrinderRegistry;
import ports.jurassicraft.hatching.HatcheryRecipes;
import ports.jurassicraft.hatching.HatcheryRegistry;
import ports.jurassicraft.machine.DnaMachineRegistry;
import ports.jurassicraft.machine.DnaMachineBlockEntity;
import ports.jurassicraft.plants.PlantRegistry;
import ports.jurassicraft.plants.PlantSpecies;

/** JEI-independent recipe descriptions, assembled only after the item registry is ready. */
public final class RecipePreviews {
    public enum Kind {
        CLEANING("cleaning"), GRINDING("grinding"), EXTRACTING("extracting"), SEQUENCING("sequencing"),
        COMBINING("combining"), SYNTHESIZING("synthesizing"), EMBRYONIC("embryonic"),
        CALCIFYING("calcifying"), INCUBATING("incubating"), CULTIVATING("cultivating"),
        SKELETON("skeleton"), BREEDING("breeding"), PEAT("peat");
        public final String id;
        Kind(String id) { this.id = id; }
    }
    /** Every inner list contains alternatives for a single slot, not simultaneous products. */
    public record Preview(Kind kind, List<List<ItemStack>> inputs, List<List<ItemStack>> outputs, String note) {}
    public record FigureCycle(ItemStack input, ItemStack output) {}
    private static final String EXAMPLE_GENES = "ACGTACGTAC";
    private RecipePreviews() {}

    public static List<FigureCycle> figureCycles() {
        List<FigureCycle> result = new ArrayList<>();
        List<Species> order = ActionFigureCycleRecipe.ORDER;
        for (int i = 0; i < order.size(); i++)
            result.add(new FigureCycle(ActionFigureItem.create(order.get(i)), ActionFigureItem.create(order.get((i + 1) % order.size()))));
        return result;
    }

    public static String figureSubtype(ItemStack stack) { return ActionFigureItem.species(stack).id(); }

    public static ItemStack catalyst(Kind kind) {
        return new ItemStack(switch (kind) {
            case CLEANING -> CleaningStationRegistry.ITEM.get();
            case GRINDING -> FossilGrinderRegistry.FOSSIL_GRINDER_ITEM.get();
            case EXTRACTING -> DnaMachineRegistry.EXTRACTOR.get().asItem();
            case SEQUENCING -> DnaMachineRegistry.SEQUENCER.get().asItem();
            case COMBINING -> DnaMachineRegistry.COMBINATOR.get().asItem();
            case SYNTHESIZING -> DnaMachineRegistry.SYNTHESIZER.get().asItem();
            case EMBRYONIC -> HatcheryRegistry.block(HatcheryRegistry.Machine.EMBRYONIC).asItem();
            case CALCIFYING -> HatcheryRegistry.block(HatcheryRegistry.Machine.CALCIFIER).asItem();
            case INCUBATING -> HatcheryRegistry.block(HatcheryRegistry.Machine.INCUBATOR).asItem();
            case CULTIVATING -> CultivatorRegistry.ITEM.get();
            case SKELETON -> AssemblyRegistry.TABLE_ITEM.get();
            case BREEDING -> BugRegistry.CRATE_ITEM.get();
            case PEAT -> PlantRegistry.PEAT.get().asItem();
        });
    }

    /** DNA lookup ignores the genome, which is copied through but does not alter recipe eligibility. */
    public static String dnaSubtype(ItemStack stack) {
        if (DnaMachineBlockEntity.isBlankDisc(stack)) return "blank";
        return Genetics.read(stack.getTag()).map(dna -> dna.kind() + ":" + dna.species() + ":" + dna.quality())
                .orElse("invalid");
    }

    public static Map<Kind, List<Preview>> build() {
        Map<Kind, List<Preview>> recipes = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) recipes.put(kind, new ArrayList<>());
        RandomSource random = RandomSource.create(42);
        ItemStack blank = new ItemStack(GeneticsRegistry.STORAGE_DISC.get());
        List<ItemStack> land = new ArrayList<>(), sea = new ArrayList<>(), plants = new ArrayList<>();
        for (Species species : Species.values()) {
            List<ItemStack> discs = animalDiscs(species);
            (species == Species.COELACANTH ? sea : land).addAll(discs);
            ItemStack tissue = new ItemStack(GeneticsRegistry.softTissue(species).get());
            List<ItemStack> allDiscs = allQualities(discs);
            add(recipes, Kind.SEQUENCING, slots(tissue, blank), List.of(allDiscs), "sequence");
            ItemStack meat = new ItemStack(DinosaurRegistry.rawMeat(species).get());
            Genetics.write(meat.getOrCreateTag(), DNAData.dinosaur(species, 100, EXAMPLE_GENES));
            add(recipes, Kind.EXTRACTING, slots(meat, blank), List.of(allDiscs), "meat");
            ItemStack genericMeat = stack(DinosaurRegistry.DINOSAUR_MEAT.get());
            genericMeat.setTag(meat.getTag().copy());
            add(recipes, Kind.EXTRACTING, slots(genericMeat, blank), List.of(allDiscs), "meat");
            genetics(recipes, discs, Genetics.synthesize(discs.get(19)));

            ItemStack tube = Genetics.synthesize(discs.get(19));
            ItemStack syringe = HatcheryRecipes.embryonic(tube, stack(ComponentsRegistry.PETRI_DISH.get()), stack(ComponentsRegistry.EMPTY_SYRINGE.get()));
            add(recipes, Kind.EMBRYONIC, slots(tube, stack(ComponentsRegistry.PETRI_DISH.get()), stack(ComponentsRegistry.EMPTY_SYRINGE.get())), List.of(List.of(syringe)), "preserve");
            if (species != Species.COELACANTH) {
                ItemStack egg = HatcheryRecipes.calcify(syringe, stack(Items.EGG));
                add(recipes, Kind.CALCIFYING, slots(syringe, stack(Items.EGG)), List.of(List.of(egg)), "preserve");
                add(recipes, Kind.INCUBATING, slots(egg, stack(HatcheryRegistry.PEAT_MOSS_ITEM.get())),
                        List.of(List.of(HatcheryRecipes.incubate(egg, 0), HatcheryRecipes.incubate(egg, 100))), "temperature");
            }
            List<ItemStack> bones = JurassicContent.BONES.get(species).values().stream().map(b -> stack(b.get())).toList();
            add(recipes, Kind.CLEANING, slots(stack(JurassicContent.ENCASED_FOSSILS.get(species).get().asItem())), List.of(bones), "cleaning");
            for (var bone : JurassicContent.BONES.get(species).values())
                add(recipes, Kind.GRINDING, slots(stack(bone.get())), List.of(List.of(stack(Items.BONE_MEAL), stack(Items.FLINT), tissue)), "bone");
            for (var bone : AssemblyRegistry.FRESH_BONES.get(species).values()) {
                ItemStack fresh = stack(bone.get());
                add(recipes, Kind.GRINDING, slots(fresh), List.of(List.of(FossilGrinderRecipes.grind(fresh, random))), "preserve");
            }
            skeleton(recipes, species, false);
            skeleton(recipes, species, true);
        }
        for (PlantSpecies species : PlantSpecies.values()) if (species.isPrehistoric()) {
            List<ItemStack> discs = plantDiscs(species);
            plants.addAll(discs);
            add(recipes, Kind.SEQUENCING, slots(stack(GeneticsRegistry.plantTissue(species).get()), blank), List.of(allQualities(discs)), "sequence");
            ItemStack tube = Genetics.synthesize(discs.get(19));
            genetics(recipes, discs, tube);
            ItemStack dish = stack(ComponentsRegistry.PLANT_CELLS_PETRI_DISH.get());
            ItemStack emptySyringe = stack(ComponentsRegistry.EMPTY_SYRINGE.get());
            add(recipes, Kind.EMBRYONIC, slots(tube, dish, emptySyringe),
                    List.of(List.of(HatcheryRecipes.embryonic(tube, dish, emptySyringe))), "preserve");
            if (species.isTree()) {
                ItemStack log = stack(PlantRegistry.petrifiedLog(species).get().asItem());
                add(recipes, Kind.GRINDING, slots(log), List.of(List.of(stack(Items.BONE_MEAL), stack(Items.FLINT), stack(GeneticsRegistry.plantTissue(species).get()))), "bone");
            }
        }
        add(recipes, Kind.EXTRACTING, slots(stack(JurassicContent.AMBER.get()), blank), List.of(land), "random_dna");
        add(recipes, Kind.EXTRACTING, slots(stack(JurassicContent.SEA_LAMPREY.get()), blank), List.of(sea), "random_dna");
        add(recipes, Kind.EXTRACTING, slots(stack(JurassicContent.AMBER_APHID.get()), blank), List.of(plants), "random_dna");
        add(recipes, Kind.CLEANING, slots(stack(JurassicContent.PLANT_FOSSIL_BLOCK.get().asItem())), List.of(List.of(
                stack(JurassicContent.PLANT_FOSSIL.get()), stack(JurassicContent.TWIG_FOSSIL.get()), stack(Items.COAL), stack(Items.FLINT), stack(Items.COBBLESTONE))), "cleaning");
        for (int variant = 0; variant < 3; variant++) {
            List<ItemStack> eggOutcomes = new ArrayList<>(List.of(stack(Items.FLINT), stack(Items.BONE_MEAL)));
            for (int count = 1; count <= 7; count++) eggOutcomes.add(new ItemStack(FossilizedEggRegistry.variant(variant).get(), count));
            add(recipes, Kind.CLEANING, slots(stack(FossilNestRegistry.nest(variant, true).get().asItem())), List.of(eggOutcomes), "nest");
            List<ItemStack> outputs = new ArrayList<>(List.of(stack(Items.BONE_MEAL), stack(Items.FLINT)));
            for (Species species : Species.values()) outputs.add(stack(GeneticsRegistry.softTissue(species).get()));
            add(recipes, Kind.GRINDING, slots(stack(FossilizedEggRegistry.variant(variant).get())), List.of(outputs), "egg");
        }
        for (boolean tree : new boolean[]{false, true}) {
            List<ItemStack> outputs = new ArrayList<>(List.of(stack(Items.BONE_MEAL), stack(Items.FLINT)));
            for (PlantSpecies species : PlantSpecies.values()) if (tree ? species.isTree() : species.isPrehistoric() && !species.isTree())
                outputs.add(stack(GeneticsRegistry.plantTissue(species).get()));
            add(recipes, Kind.GRINDING, slots(stack(tree ? JurassicContent.TWIG_FOSSIL.get() : JurassicContent.PLANT_FOSSIL.get())), List.of(outputs), "plant_fossil");
        }
        breeding(recipes);
        cultivation(recipes);
        add(recipes, Kind.PEAT, slots(stack(PlantRegistry.PEAT.get().asItem()), stack(PlantRegistry.MOSS.get().asItem())),
                List.of(List.of(stack(PlantRegistry.MOSS.get().asItem()))), "peat");
        return recipes;
    }

    private static void genetics(Map<Kind, List<Preview>> recipes, List<ItemStack> discs, ItemStack tube) {
        List<ItemStack> allDiscs = allQualities(discs);
        add(recipes, Kind.COMBINING, List.of(allDiscs, allDiscs), List.of(allDiscs), "combine");
        add(recipes, Kind.SYNTHESIZING, slots(discs.get(19), stack(ComponentsRegistry.EMPTY_TEST_TUBE.get()), stack(ComponentsRegistry.DNA_NUCLEOTIDES.get())), List.of(List.of(tube)), "synthesize");
    }
    private static List<ItemStack> allQualities(List<ItemStack> randomDiscs) {
        DNAData sample = Genetics.read(randomDiscs.get(0).getTag()).orElseThrow();
        List<ItemStack> result = new ArrayList<>();
        for (int quality = 0; quality <= 100; quality++) result.add(Genetics.disc(null, sample.withQuality(quality)));
        return result;
    }
    private static List<ItemStack> animalDiscs(Species species) {
        List<ItemStack> result = new ArrayList<>();
        for (int quality = 5; quality <= 100; quality += 5) result.add(Genetics.disc(null, DNAData.dinosaur(species, quality, EXAMPLE_GENES)));
        return result;
    }
    private static List<ItemStack> plantDiscs(PlantSpecies species) {
        List<ItemStack> result = new ArrayList<>();
        for (int quality = 5; quality <= 100; quality += 5) result.add(Genetics.disc(null, DNAData.plant(species, quality)));
        return result;
    }
    private static void skeleton(Map<Kind, List<Preview>> recipes, Species species, boolean fresh) {
        String[][] layout = SkeletonRecipes.layout(species);
        List<List<ItemStack>> grid = new ArrayList<>();
        var bones = (fresh ? AssemblyRegistry.FRESH_BONES : JurassicContent.BONES).get(species);
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) {
            String bone = y < layout.length && x < layout[y].length ? layout[y][x] : "";
            grid.add(bone.isEmpty() ? List.of() : List.of(stack(bones.get(bone).get())));
        }
        add(recipes, Kind.SKELETON, grid, List.of(List.of(SkeletonDisplayItem.create(species, fresh))), "skeleton");
    }
    private static void breeding(Map<Kind, List<Preview>> recipes) {
        for (Item bug : List.of(BugRegistry.CRICKETS.get(), BugRegistry.COCKROACHES.get(), BugRegistry.MEALWORM_BEETLES.get())) {
            ItemStack parent = stack(bug);
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                ItemStack food = stack(item);
                int count = BugFoods.offspring(parent, food);
                if (count > 0) add(recipes, Kind.BREEDING, slots(parent, food), List.of(List.of(new ItemStack(bug, count))), "breeding");
            }
        }
    }
    private static void cultivation(Map<Kind, List<Preview>> recipes) {
        ItemStack tube = Genetics.synthesize(animalDiscs(Species.COELACANTH).get(19));
        ItemStack syringe = HatcheryRecipes.embryonic(tube, stack(ComponentsRegistry.PETRI_DISH.get()), stack(ComponentsRegistry.EMPTY_SYRINGE.get()));
        List<ItemStack> foods = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) if (CultivatorNutrition.get(stack(item)) != null) foods.add(stack(item));
        ItemStack female = stack(HatcheryRegistry.hatchedEgg(Species.COELACANTH));
        female.setTag(syringe.getTag().copy()); female.getTag().putBoolean("Gender", false);
        ItemStack male = female.copy(); male.getTag().putBoolean("Gender", true);
        add(recipes, Kind.CULTIVATING, List.of(List.of(syringe), List.of(stack(Items.WATER_BUCKET)), foods), List.of(List.of(female, male)), "cultivate");
    }
    private static ItemStack stack(Item item) { return new ItemStack(item); }
    private static List<List<ItemStack>> slots(ItemStack... stacks) {
        List<List<ItemStack>> result = new ArrayList<>();
        for (ItemStack stack : stacks) result.add(List.of(stack));
        return result;
    }
    private static void add(Map<Kind, List<Preview>> recipes, Kind kind, List<List<ItemStack>> inputs, List<List<ItemStack>> outputs, String note) {
        recipes.get(kind).add(new Preview(kind, inputs, outputs, note));
    }
}
