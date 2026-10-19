package ports.jurassicraft.validation;

import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ports.jurassicraft.components.ComponentsRegistry;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.cultivator.CultivatorBlockEntity;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.grinder.FossilGrinderRecipes;
import ports.jurassicraft.hatching.HatcheryRecipes;
import ports.jurassicraft.hatching.HatcheryRegistry;
import ports.jurassicraft.plantculture.PlantCultureRegistry;
import ports.jurassicraft.plants.PlantRegistry;
import ports.jurassicraft.plants.PlantSpecies;

/** Check the pure revival operations with registered items during runData, without a world. */
final class RevivalDataValidation {
    private static final String MARKER = "revival-validation";
    private RevivalDataValidation() {}

    static void validate(List<String> errors) {
        for (Species species : Species.values()) {
            try { animal(species); }
            catch (RuntimeException ex) { errors.add("Revival chain " + species.id() + ": " + ex); }
        }
        for (PlantSpecies species : PlantSpecies.values()) if (species.isPrehistoric()) {
            try { plant(species); }
            catch (RuntimeException ex) { errors.add("Plant revival " + species.id() + ": " + ex); }
        }
        for (boolean tree : new boolean[] {false, true}) {
            try { plantFossil(tree); }
            catch (RuntimeException ex) { errors.add((tree ? "Twig" : "Plant") + " fossil grinder: " + ex); }
        }
    }

    private static void animal(Species species) {
        ItemStack fossil = new ItemStack(JurassicContent.BONES.get(species).get("skull").get());
        fossil.getOrCreateTag().putString("SampleMarker", MARKER);
        require(FossilGrinderRecipes.accepts(fossil), "fossil skull is not accepted by the grinder");
        RandomSource random = RandomSource.create(42L + species.legacyId());
        ItemStack tissue = ItemStack.EMPTY;
        boolean boneMeal = false, flint = false;
        for (int i = 0; i < 128; i++) {
            ItemStack output = FossilGrinderRecipes.grind(fossil, random);
            if (output.is(Items.BONE_MEAL)) boneMeal = true;
            else if (output.is(Items.FLINT)) flint = true;
            else if (output.is(GeneticsRegistry.softTissue(species).get())) {
                marker(output);
                tissue = output;
            } else throw new IllegalStateException("grinder returned an unexpected fossil output");
        }
        require(boneMeal && flint && !tissue.isEmpty(), "grinder must produce meal, flint, and matching tissue");
        ItemStack disc = Genetics.sequence(tissue, random);
        DNAData sequenced = dna(disc);
        require(sequenced.dinosaur() == species, "sequencing changed dinosaur identity");
        marker(disc);
        ItemStack complete = complete(disc, sequenced);
        ItemStack tube = Genetics.synthesize(complete);
        require(tube.is(GeneticsRegistry.dinosaurDna(species).get()), "synthesis produced the wrong DNA tube");
        DNAData expected = dna(tube);
        require(expected.dinosaur() == species && expected.quality() == 100
                && expected.genetics().equals(sequenced.genetics()), "synthesis changed DNA");
        marker(tube);
        ItemStack embryo = HatcheryRecipes.embryonic(tube, new ItemStack(ComponentsRegistry.PETRI_DISH.get()),
                new ItemStack(ComponentsRegistry.EMPTY_SYRINGE.get()));
        require(embryo.is(HatcheryRegistry.syringe(species)), "embryonic machine produced the wrong syringe");
        preserved(embryo, expected);
        if (species == Species.COELACANTH) {
            require(CultivatorBlockEntity.validSyringe(embryo) && CultivatorBlockEntity.accepts(0, embryo),
                    "coelacanth syringe is not accepted by the cultivator");
            require(HatcheryRecipes.calcify(embryo, new ItemStack(Items.EGG)).isEmpty(),
                    "coelacanth must use the cultivator rather than an egg");
            return;
        }
        require(!CultivatorBlockEntity.validSyringe(embryo), "cultivator accepted a non-coelacanth syringe");
        ItemStack egg = HatcheryRecipes.calcify(embryo, new ItemStack(Items.EGG));
        require(egg.is(HatcheryRegistry.egg(species)), "calcifier produced the wrong egg");
        preserved(egg, expected);
        for (int temperature : new int[] {50, 51}) {
            ItemStack hatched = HatcheryRecipes.incubate(egg, temperature);
            require(hatched.is(HatcheryRegistry.hatchedEgg(species)), "incubator produced the wrong hatchling item");
            preserved(hatched, expected);
            require(hatched.getTag().getBoolean("Gender") == (temperature > 50), "incubator sex threshold is incorrect");
        }
        require(HatcheryRecipes.calcify(embryo, new ItemStack(Items.ARROW)).isEmpty(), "calcifier accepted a non-egg");
    }

    private static void plant(PlantSpecies species) {
        ItemStack tissue = new ItemStack(GeneticsRegistry.plantTissue(species).get());
        tissue.getOrCreateTag().putString("SampleMarker", MARKER);
        RandomSource random = RandomSource.create(1000L + species.ordinal());
        if (species.isTree()) {
            ItemStack log = new ItemStack(PlantRegistry.petrifiedLog(species).get().asItem());
            log.getOrCreateTag().putString("SampleMarker", MARKER);
            require(FossilGrinderRecipes.accepts(log), "petrified log is not accepted by the grinder");
            ItemStack ground = ItemStack.EMPTY;
            for (int i = 0; i < 128 && ground.isEmpty(); i++) {
                ItemStack output = FossilGrinderRecipes.grind(log, random);
                if (output.is(GeneticsRegistry.plantTissue(species).get())) ground = output;
                else require(output.is(Items.BONE_MEAL) || output.is(Items.FLINT), "unexpected petrified-log output");
            }
            require(!ground.isEmpty(), "petrified log never produced matching tissue");
            marker(ground);
            tissue = ground;
        }
        ItemStack disc = Genetics.sequence(tissue, random);
        DNAData sequenced = dna(disc);
        require(sequenced.plant() == species, "sequencing changed plant identity");
        marker(disc);
        ItemStack tube = Genetics.synthesize(complete(disc, sequenced));
        require(tube.is(GeneticsRegistry.plantDna(species).get()), "synthesis produced the wrong plant DNA tube");
        DNAData expected = dna(tube);
        require(expected.plant() == species && expected.quality() == 100, "synthesis changed plant DNA");
        marker(tube);
        ItemStack callus = HatcheryRecipes.embryonic(tube, new ItemStack(ComponentsRegistry.PLANT_CELLS_PETRI_DISH.get()),
                new ItemStack(ComponentsRegistry.EMPTY_SYRINGE.get()));
        require(callus.is(PlantCultureRegistry.callus(species).get()), "embryonic machine produced the wrong callus");
        preserved(callus, expected);
    }

    private static void plantFossil(boolean tree) {
        EnumSet<PlantSpecies> pending = EnumSet.noneOf(PlantSpecies.class);
        Map<Item, PlantSpecies> tissues = new IdentityHashMap<>();
        for (PlantSpecies species : PlantSpecies.values()) if (species.isPrehistoric() && species.isTree() == tree) {
            pending.add(species);
            tissues.put(GeneticsRegistry.plantTissue(species).get(), species);
        }
        ItemStack input = new ItemStack((tree ? JurassicContent.TWIG_FOSSIL : JurassicContent.PLANT_FOSSIL).get());
        input.getOrCreateTag().putString("SampleMarker", MARKER);
        require(FossilGrinderRecipes.accepts(input), "fossil is not accepted");
        boolean boneMeal = false, flint = false;
        RandomSource random = RandomSource.create(tree ? 77L : 88L);
        for (int i = 0; i < 4096 && (!pending.isEmpty() || !boneMeal || !flint); i++) {
            ItemStack output = FossilGrinderRecipes.grind(input, random);
            if (output.is(Items.BONE_MEAL)) boneMeal = true;
            else if (output.is(Items.FLINT)) flint = true;
            else {
                PlantSpecies selected = tissues.get(output.getItem());
                require(selected != null, "unexpected or wrong-kind plant tissue");
                marker(output);
                pending.remove(selected);
            }
        }
        require(boneMeal && flint && pending.isEmpty(), "missing expected grinder outcomes: " + pending);
    }

    private static ItemStack complete(ItemStack disc, DNAData initial) {
        ItemStack combined = Genetics.combine(disc, disc);
        DNAData current = dna(combined);
        require(current.kind() == initial.kind() && current.species().equals(initial.species())
                && current.genetics().equals(initial.genetics()), "combining changed genetic identity");
        marker(combined);
        for (int i = 0; current.quality() < 100 && i < 20; i++) {
            combined = Genetics.combine(combined, disc);
            current = dna(combined);
        }
        require(current.quality() == 100, "combining did not produce complete DNA");
        return combined;
    }
    private static DNAData dna(ItemStack stack) {
        return Genetics.read(stack.getTag()).orElseThrow(() -> new IllegalStateException("missing or malformed DNA"));
    }
    private static void preserved(ItemStack stack, DNAData expected) {
        require(dna(stack).equals(expected), "genetic data did not survive the operation");
        marker(stack);
    }
    private static void marker(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        require(tag != null && MARKER.equals(tag.getString("SampleMarker")), "sample metadata was lost");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
