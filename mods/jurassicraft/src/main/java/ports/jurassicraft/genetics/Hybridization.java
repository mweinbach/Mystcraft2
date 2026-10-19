package ports.jurassicraft.genetics;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import ports.jurassicraft.content.Species;

/** The original hybridizer matched complete dinosaur discs against registered hybrid species. */
public final class Hybridization {
    // The imported 1.12 release registers no Hybrid implementations. Add a recipe only
    // alongside an actual ported hybrid species, never by substituting an ordinary animal.
    private static final List<Recipe> RECIPES = List.of();
    private Hybridization() {}

    record Recipe(Species result, List<Species> parents) {
        Recipe {
            parents = List.copyOf(parents);
            if (result == null || parents.size() < 2 || parents.size() > 8
                    || EnumSet.copyOf(parents).size() != parents.size())
                throw new IllegalArgumentException("Hybrid recipes require two to eight distinct parents");
        }
    }

    public static boolean hasRecipes() { return !RECIPES.isEmpty(); }
    public static ItemStack hybridize(List<ItemStack> inputs) { return RECIPES.isEmpty() ? ItemStack.EMPTY : hybridize(inputs, RECIPES); }

    static ItemStack hybridize(List<ItemStack> inputs, List<Recipe> recipes) {
        if (inputs.size() != 8) return ItemStack.EMPTY;
        EnumSet<Species> species = EnumSet.noneOf(Species.class);
        DNAData first = null;
        CompoundTag metadata = null;
        for (ItemStack input : inputs) {
            if (input.isEmpty()) continue;
            if (!input.is(GeneticsRegistry.STORAGE_DISC.get())) return ItemStack.EMPTY;
            Optional<DNAData> parsed = Genetics.read(input.getTag());
            if (parsed.isEmpty() || parsed.get().kind() != DNAData.Kind.DINOSAUR || parsed.get().quality() != 100
                    || !species.add(parsed.get().dinosaur())) return ItemStack.EMPTY;
            if (first == null) {
                first = parsed.get();
                metadata = input.getTag().copy();
            }
        }
        if (first == null) return ItemStack.EMPTY;
        for (Recipe recipe : recipes) {
            if (species.size() == recipe.parents().size() && species.containsAll(recipe.parents()))
                return Genetics.disc(metadata, DNAData.dinosaur(recipe.result(), 100, first.genetics()));
        }
        return ItemStack.EMPTY;
    }
}
