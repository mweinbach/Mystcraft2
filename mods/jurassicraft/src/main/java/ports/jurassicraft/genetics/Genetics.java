package ports.jurassicraft.genetics;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.plants.PlantSpecies;

/** DNA serialization and machine operations. These functions never mutate their inputs. */
public final class Genetics {
    private static final List<Species> LAND_ANIMALS = Arrays.stream(Species.values()).filter(s -> s != Species.COELACANTH).toList();
    private static final List<PlantSpecies> PLANTS = Arrays.stream(PlantSpecies.values()).filter(PlantSpecies::isPrehistoric).toList();
    // Frozen 1.12.2 PlantHandler IDs; adding/reordering enum values must not reinterpret saved DNA.
    private static final List<String> LEGACY_PLANTS = List.of(
            "ajuginucula_smithii", "small_royal_fern", "calamites", "small_chain_fern", "small_cycad",
            "ginkgo", "bennettitalean_cycadeoidea", "cry_pansy", "scaly_tree_fern", "cycad_zamites",
            "dicksonia", "wild_onion", "dicroidium_zuberi", "dictyophyllum", "west_indian_lilac",
            "serenna_veriformans", "ladinia_simplex", "orontium_mackii", "umaltolepis", "liriodendrites",
            "raphaelia", "encephalartos", "psaronius", "phoenix", "wild_potato", "araucaria",
            "cinnamon_fern", "bristle_fern", "tempskya", "woolly_stalked_begonia",
            "largestipule_leather_root", "rhacophyton", "graminidites_bambusoides", "enallhelia", "aulopora",
            "cladochonus", "lithostrotion", "stylophyllopsis", "hippurites_radiosus", "heliconia", "rhamnus_salifocifus");
    private static final String[] GENETIC_KEYS = {"DNAQuality", "Genetics", "StorageId", "SpeciesId", "Dinosaur", "Plant"};
    private Genetics() {}

    /** Upstream quality is uniformly distributed in increments of five, from 5 to 100. */
    public static int randomQuality(RandomSource random) { return (random.nextInt(20) + 1) * 5; }
    public static String randomGenetics(RandomSource random) {
        StringBuilder code = new StringBuilder(10);
        for (int i = 0; i < 10; i++) code.append("ACGT".charAt(random.nextInt(4)));
        return code.toString();
    }

    /** Read both original numeric identities and our stable species IDs; reject contradictions. */
    public static Optional<DNAData> read(CompoundTag tag) {
        if (tag == null || !tag.contains("DNAQuality", Tag.TAG_INT)) return Optional.empty();
        if (tag.contains("StorageId") && !tag.contains("StorageId", Tag.TAG_STRING)) return Optional.empty();
        String storage = tag.getString("StorageId");
        DNAData.Kind kind;
        if (storage.equals("DinoDNA")) kind = DNAData.Kind.DINOSAUR;
        else if (storage.equals("PlantDNA")) kind = DNAData.Kind.PLANT;
        else if (storage.isEmpty() && !tag.contains("StorageId") && tag.contains("Dinosaur")) kind = DNAData.Kind.DINOSAUR;
        else if (storage.isEmpty() && !tag.contains("StorageId") && tag.contains("Plant")) kind = DNAData.Kind.PLANT;
        else return Optional.empty();
        if (tag.contains("SpeciesId") && !tag.contains("SpeciesId", Tag.TAG_STRING)) return Optional.empty();
        if (kind == DNAData.Kind.DINOSAUR) {
            if (tag.contains("Plant") || !tag.contains("Genetics", Tag.TAG_STRING)) return Optional.empty();
            Species animal = null;
            if (tag.contains("Dinosaur")) {
                if (!tag.contains("Dinosaur", Tag.TAG_INT)) return Optional.empty();
                for (Species candidate : Species.values()) if (candidate.legacyId() == tag.getInt("Dinosaur")) animal = candidate;
                if (animal == null) return Optional.empty();
            }
            if (tag.contains("SpeciesId")) {
                Species named = DNAData.findDinosaur(tag.getString("SpeciesId"));
                if (named == null || (animal != null && animal != named)) return Optional.empty();
                animal = named;
            }
            if (animal == null) return Optional.empty();
            try { return Optional.of(DNAData.dinosaur(animal, tag.getInt("DNAQuality"), tag.getString("Genetics"))); }
            catch (IllegalArgumentException invalid) { return Optional.empty(); }
        }
        if (tag.contains("Dinosaur") || tag.contains("Genetics")) return Optional.empty();
        PlantSpecies plant = null;
        if (tag.contains("Plant")) {
            if (!tag.contains("Plant", Tag.TAG_INT)) return Optional.empty();
            int legacyId = tag.getInt("Plant");
            if (legacyId < 0 || legacyId >= LEGACY_PLANTS.size()) return Optional.empty();
            plant = DNAData.findPlant(LEGACY_PLANTS.get(legacyId));
            if (plant == null) return Optional.empty();
        }
        if (tag.contains("SpeciesId")) {
            PlantSpecies named = DNAData.findPlant(tag.getString("SpeciesId"));
            if (named == null || (plant != null && plant != named)) return Optional.empty();
            plant = named;
        }
        if (plant == null) return Optional.empty();
        try { return Optional.of(DNAData.plant(plant, tag.getInt("DNAQuality"))); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    /** Update genetics keys in the target without disturbing other sample metadata. */
    public static void write(CompoundTag tag, DNAData dna) {
        for (String key : GENETIC_KEYS) tag.remove(key);
        tag.putInt("DNAQuality", dna.quality());
        tag.putString("SpeciesId", dna.species());
        if (dna.kind() == DNAData.Kind.DINOSAUR) {
            tag.putString("StorageId", "DinoDNA");
            tag.putInt("Dinosaur", dna.dinosaur().legacyId());
            tag.putString("Genetics", dna.genetics());
        } else {
            tag.putString("StorageId", "PlantDNA");
            int legacyId = LEGACY_PLANTS.indexOf(dna.species());
            if (legacyId >= 0) tag.putInt("Plant", legacyId);
        }
    }

    /** Check eligibility independently of the randomized extension result. */
    public static boolean canSequence(ItemStack tissue) {
        if (tissue.isEmpty()) return false;
        Sequencable custom = extension(tissue, Sequencable.class);
        return custom != null ? custom.isSequencable(tissue.copy()) : !sequence(tissue, RandomSource.create(0)).isEmpty();
    }

    /** Sequence a sample into a disc. Existing built-in DNA is copied, not rerolled. */
    public static ItemStack sequence(ItemStack tissue, RandomSource random) {
        if (tissue.isEmpty()) return ItemStack.EMPTY;
        Sequencable custom = extension(tissue, Sequencable.class);
        if (custom != null) {
            if (!custom.isSequencable(tissue.copy())) return ItemStack.EMPTY;
            ItemStack result = custom.sequence(tissue.copy(), random);
            return result == null ? ItemStack.EMPTY : result.copy();
        }
        for (Species species : Species.values()) {
            if (tissue.is(GeneticsRegistry.softTissue(species).get()))
                return sequenceAnimal(tissue, species, random);
        }
        for (PlantSpecies species : PLANTS) {
            if (tissue.is(GeneticsRegistry.plantTissue(species).get()))
                return sequencePlant(tissue, species, random);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack sequenceAnimal(ItemStack sample, Species species, RandomSource random) {
        CompoundTag tag = copyTag(sample);
        if (!identityMatches(tag, DNAData.Kind.DINOSAUR, species.id())) return ItemStack.EMPTY;
        if (!tag.contains("DNAQuality") && !tag.contains("Genetics")) {
            return disc(tag, DNAData.dinosaur(species, randomQuality(random), randomGenetics(random)));
        }
        // Old tissue could carry genome/quality but rely on the item's old metadata for species.
        if (!tag.contains("Dinosaur") && !tag.contains("SpeciesId")) tag.putInt("Dinosaur", species.legacyId());
        if (!tag.contains("StorageId")) tag.putString("StorageId", "DinoDNA");
        Optional<DNAData> dna = read(tag);
        return dna.filter(value -> value.kind() == DNAData.Kind.DINOSAUR && value.dinosaur() == species)
                .map(value -> disc(tag, value)).orElse(ItemStack.EMPTY);
    }

    private static ItemStack sequencePlant(ItemStack sample, PlantSpecies species, RandomSource random) {
        CompoundTag tag = copyTag(sample);
        if (!identityMatches(tag, DNAData.Kind.PLANT, species.id())) return ItemStack.EMPTY;
        if (!tag.contains("DNAQuality") && !tag.contains("Genetics"))
            return disc(tag, DNAData.plant(species, randomQuality(random)));
        if (!tag.contains("Plant") && !tag.contains("SpeciesId")) tag.putString("SpeciesId", species.id());
        if (!tag.contains("StorageId")) tag.putString("StorageId", "PlantDNA");
        Optional<DNAData> dna = read(tag);
        return dna.filter(value -> value.kind() == DNAData.Kind.PLANT && value.plant() == species)
                .map(value -> disc(tag, value)).orElse(ItemStack.EMPTY);
    }

    /** Extract from mosquito/aphid amber, sea lamprey, or a tagged raw dinosaur meat sample. */
    public static ItemStack extract(ItemStack sample, RandomSource random) {
        if (sample.isEmpty()) return ItemStack.EMPTY;
        if (sample.is(DinosaurRegistry.DINOSAUR_MEAT.get())) {
            return read(sample.getTag()).filter(dna -> dna.kind() == DNAData.Kind.DINOSAUR)
                    .map(dna -> disc(copyTag(sample), dna)).orElse(ItemStack.EMPTY);
        }
        for (Species species : Species.values()) {
            if (sample.is(DinosaurRegistry.rawMeat(species).get())) {
                return read(sample.getTag())
                        .filter(dna -> dna.kind() == DNAData.Kind.DINOSAUR && dna.dinosaur() == species)
                        .map(dna -> disc(copyTag(sample), dna)).orElse(ItemStack.EMPTY);
            }
        }
        boolean animal = sample.is(JurassicContent.AMBER.get()) || sample.is(JurassicContent.SEA_LAMPREY.get());
        boolean plant = sample.is(JurassicContent.AMBER_APHID.get());
        if (!animal && !plant) return ItemStack.EMPTY;
        CompoundTag tag = copyTag(sample);
        if (hasGeneticData(tag)) {
            return read(tag).filter(dna -> animal == (dna.kind() == DNAData.Kind.DINOSAUR))
                    .filter(dna -> !sample.is(JurassicContent.SEA_LAMPREY.get()) || dna.dinosaur() == Species.COELACANTH)
                    .filter(dna -> !sample.is(JurassicContent.AMBER.get()) || dna.dinosaur() != Species.COELACANTH)
                    .map(dna -> disc(tag, dna)).orElse(ItemStack.EMPTY);
        }
        if (plant) return disc(tag, DNAData.plant(PLANTS.get(random.nextInt(PLANTS.size())), randomQuality(random)));
        Species species = sample.is(JurassicContent.SEA_LAMPREY.get()) ? Species.COELACANTH : LAND_ANIMALS.get(random.nextInt(LAND_ANIMALS.size()));
        return disc(tag, DNAData.dinosaur(species, randomQuality(random), randomGenetics(random)));
    }

    /** Combine two discs of the same species, retaining the first animal's genetic code. */
    public static ItemStack combine(ItemStack first, ItemStack second) {
        if (!first.is(GeneticsRegistry.STORAGE_DISC.get()) || !second.is(GeneticsRegistry.STORAGE_DISC.get())) return ItemStack.EMPTY;
        Optional<DNAData> left = read(first.getTag()), right = read(second.getTag());
        if (left.isEmpty() || right.isEmpty()) return ItemStack.EMPTY;
        DNAData a = left.get(), b = right.get();
        if (a.kind() != b.kind() || !a.species().equals(b.species())) return ItemStack.EMPTY;
        return disc(copyTag(first), a.withQuality(Math.min(100, a.quality() + b.quality())));
    }

    /** A 100% disc synthesizes into a DNA tube without losing genetic data. */
    public static ItemStack synthesize(ItemStack disc) {
        return synthesize(disc, RandomSource.create(0));
    }

    /** Check eligibility without rolling a potentially random extension result. */
    public static boolean canSynthesize(ItemStack disc) {
        if (disc.isEmpty()) return false;
        Synthesizable custom = extension(disc, Synthesizable.class);
        if (custom != null) return disc.hasTag() && disc.getTag().contains("DNAQuality") && custom.isSynthesizable(disc.copy());
        return !synthesize(disc).isEmpty();
    }

    /** Process a built-in disc or an extension input, using the supplied random source. */
    public static ItemStack synthesize(ItemStack disc, RandomSource random) {
        if (disc.isEmpty()) return ItemStack.EMPTY;
        Synthesizable custom = extension(disc, Synthesizable.class);
        if (custom != null) {
            // The original synthesizer requires this tag even on extension inputs.
            if (!disc.hasTag() || !disc.getTag().contains("DNAQuality") || !custom.isSynthesizable(disc.copy())) return ItemStack.EMPTY;
            ItemStack result = custom.synthesize(disc.copy(), random);
            return result == null ? ItemStack.EMPTY : result.copy();
        }
        if (!disc.is(GeneticsRegistry.STORAGE_DISC.get())) return ItemStack.EMPTY;
        Optional<DNAData> data = read(disc.getTag());
        if (data.isEmpty() || data.get().quality() != 100) return ItemStack.EMPTY;
        DNAData dna = data.get();
        ItemStack result = new ItemStack(dna.kind() == DNAData.Kind.DINOSAUR
                ? GeneticsRegistry.dinosaurDna(dna.dinosaur()).get() : GeneticsRegistry.plantDna(dna.plant()).get());
        CompoundTag tag = copyTag(disc);
        write(tag, dna);
        result.setTag(tag);
        return result;
    }

    private static <T> T extension(ItemStack stack, Class<T> type) {
        var item = stack.getItem();
        if (type.isInstance(item)) return type.cast(item);
        if (item instanceof BlockItem blockItem && type.isInstance(blockItem.getBlock())) return type.cast(blockItem.getBlock());
        return null;
    }

    public static ItemStack disc(CompoundTag original, DNAData dna) {
        ItemStack result = new ItemStack(GeneticsRegistry.STORAGE_DISC.get());
        CompoundTag tag = original == null ? new CompoundTag() : original.copy();
        write(tag, dna);
        result.setTag(tag);
        return result;
    }

    private static CompoundTag copyTag(ItemStack stack) { return stack.hasTag() ? stack.getTag().copy() : new CompoundTag(); }
    private static boolean hasGeneticData(CompoundTag tag) {
        for (String key : GENETIC_KEYS) if (tag.contains(key)) return true;
        return false;
    }
    private static boolean identityMatches(CompoundTag tag, DNAData.Kind kind, String species) {
        if (tag.contains("StorageId") && (!tag.contains("StorageId", Tag.TAG_STRING) ||
                !tag.getString("StorageId").equals(kind == DNAData.Kind.DINOSAUR ? "DinoDNA" : "PlantDNA"))) return false;
        if (tag.contains("SpeciesId") && (!tag.contains("SpeciesId", Tag.TAG_STRING) || !tag.getString("SpeciesId").equals(species))) return false;
        if (kind == DNAData.Kind.DINOSAUR) {
            return !tag.contains("Plant") && (!tag.contains("Dinosaur") || tag.contains("Dinosaur", Tag.TAG_INT) && tag.getInt("Dinosaur") == DNAData.findDinosaur(species).legacyId());
        }
        return !tag.contains("Dinosaur") && (!tag.contains("Plant") || tag.contains("Plant", Tag.TAG_INT) && tag.getInt("Plant") == LEGACY_PLANTS.indexOf(species));
    }
}
