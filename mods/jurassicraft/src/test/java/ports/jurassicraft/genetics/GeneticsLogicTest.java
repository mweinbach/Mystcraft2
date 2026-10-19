package ports.jurassicraft.genetics;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.plants.PlantSpecies;

/** Dependency-free logic test, run by the geneticsLogicTest Gradle task. */
public final class GeneticsLogicTest {
    public static void main(String[] args) {
        DinosaurAttributesLogicTest.run();
        for (Species species : Species.values()) {
            DNAData original = DNAData.dinosaur(species, 75, "AACCGGTTAC");
            CompoundTag tag = new CompoundTag();
            tag.putString("OtherModData", "keep me");
            Genetics.write(tag, original);
            check(Genetics.read(tag).orElseThrow().equals(original), "dinosaur roundtrip " + species);
            check(tag.getInt("Dinosaur") == species.legacyId(), "dinosaur legacy ID");
            check(tag.getString("OtherModData").equals("keep me"), "unrelated metadata");
            tag.remove("StorageId");
            tag.remove("SpeciesId");
            check(Genetics.read(tag).orElseThrow().equals(original), "legacy meat read");
            tag.putInt("Dinosaur", -1);
            check(Genetics.read(tag).isEmpty(), "unknown animal rejected");
        }
        for (PlantSpecies species : PlantSpecies.values()) {
            if (!species.isPrehistoric()) continue;
            DNAData original = DNAData.plant(species, 100);
            CompoundTag tag = new CompoundTag();
            Genetics.write(tag, original);
            check(Genetics.read(tag).orElseThrow().equals(original), "plant roundtrip " + species);
            check(tag.getInt("Plant") == species.ordinal(), "plant legacy ID");
            tag.remove("SpeciesId");
            check(Genetics.read(tag).orElseThrow().equals(original), "legacy plant read");
        }
        DNAData animal = DNAData.dinosaur(Species.VELOCIRAPTOR, 55, "ACGTACGTAC");
        CompoundTag tag = new CompoundTag();
        Genetics.write(tag, animal);
        tag.putString("SpeciesId", "triceratops");
        check(Genetics.read(tag).isEmpty(), "conflicting identity rejected");
        Genetics.write(tag, animal);
        tag.putString("Genetics", "ACGXACGTAC");
        check(Genetics.read(tag).isEmpty(), "invalid base rejected");
        tag.putString("Genetics", "ACGTACGTAC");
        tag.putInt("DNAQuality", 101);
        check(Genetics.read(tag).isEmpty(), "quality over 100 rejected");
        tag.putInt("DNAQuality", -1);
        check(Genetics.read(tag).isEmpty(), "negative quality rejected");
        tag.putString("DNAQuality", "55");
        check(Genetics.read(tag).isEmpty(), "non-numeric quality rejected");
        check(Genetics.read(null).isEmpty(), "missing data rejected");
        RandomSource random = RandomSource.create(934);
        Set<Integer> qualities = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            int quality = Genetics.randomQuality(random);
            check(quality >= 5 && quality <= 100 && quality % 5 == 0, "quality range");
            qualities.add(quality);
            check(DNAData.validGenetics(Genetics.randomGenetics(random)), "generated code");
        }
        check(qualities.size() == 20, "all 20 quality levels reachable");
        System.out.println("Genetics logic checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
