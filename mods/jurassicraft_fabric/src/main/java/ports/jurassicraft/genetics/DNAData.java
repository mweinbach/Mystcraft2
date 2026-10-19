package ports.jurassicraft.genetics;

import java.util.Objects;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.plants.PlantSpecies;

/** Immutable, validated genetics carried by samples, discs and synthesized DNA. */
public record DNAData(Kind kind, String species, int quality, String genetics) {
    public enum Kind { DINOSAUR, PLANT }

    public DNAData {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(species, "species");
        Objects.requireNonNull(genetics, "genetics");
        if (quality < 0 || quality > 100) throw new IllegalArgumentException("DNA quality must be between 0 and 100");
        if (kind == Kind.DINOSAUR) {
            if (findDinosaur(species) == null || !validGenetics(genetics)) throw new IllegalArgumentException("Invalid dinosaur DNA");
        } else if (findPlant(species) == null || !genetics.isEmpty()) {
            throw new IllegalArgumentException("Invalid plant DNA");
        }
    }

    public static DNAData dinosaur(Species species, int quality, String genetics) {
        return new DNAData(Kind.DINOSAUR, species.id(), quality, genetics);
    }

    public static DNAData plant(PlantSpecies species, int quality) {
        return new DNAData(Kind.PLANT, species.id(), quality, "");
    }

    public Species dinosaur() { return kind == Kind.DINOSAUR ? findDinosaur(species) : null; }
    public PlantSpecies plant() { return kind == Kind.PLANT ? findPlant(species) : null; }
    public DNAData withQuality(int quality) { return new DNAData(kind, species, quality, genetics); }

    public static boolean validGenetics(String genetics) {
        if (genetics == null || genetics.length() != 10) return false;
        for (int i = 0; i < genetics.length(); i++) {
            char base = genetics.charAt(i);
            if (base != 'A' && base != 'C' && base != 'G' && base != 'T') return false;
        }
        return true;
    }

    public static Species findDinosaur(String id) {
        for (Species species : Species.values()) if (species.id().equals(id)) return species;
        return null;
    }

    public static PlantSpecies findPlant(String id) {
        for (PlantSpecies species : PlantSpecies.values()) if (species.isPrehistoric() && species.id().equals(id)) return species;
        return null;
    }
}
