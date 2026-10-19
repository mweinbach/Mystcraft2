package ports.jurassicraft.grinder;

import java.util.Arrays;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.plants.PlantRegistry;
import ports.jurassicraft.plants.PlantSpecies;

/** The weighted outcomes implemented by the original GrindableItem types. */
public final class FossilGrinderRecipes {
    private static final List<PlantSpecies> PLANTS = Arrays.stream(PlantSpecies.values())
            .filter(species -> species.isPrehistoric() && !species.isTree()).toList();
    private static final List<PlantSpecies> TREES = Arrays.stream(PlantSpecies.values())
            .filter(PlantSpecies::isTree).toList();

    private FossilGrinderRecipes() {}

    // Resolve registered items lazily, after Forge has completed item registration.
    private static final class Inputs {
        private static final Map<Item, Species> BONES = bones();
        private static final Map<Item, PlantSpecies> PETRIFIED = petrified();
        private static Map<Item, Species> bones() {
            Map<Item, Species> result = new IdentityHashMap<>();
            for (Species species : Species.values())
                for (var bone : JurassicContent.BONES.get(species).values()) result.put(bone.get(), species);
            return result;
        }
        private static Map<Item, PlantSpecies> petrified() {
            Map<Item, PlantSpecies> result = new IdentityHashMap<>();
            for (PlantSpecies tree : TREES) result.put(PlantRegistry.petrifiedLog(tree).get().asItem(), tree);
            return result;
        }
    }

    private static Species boneSpecies(ItemStack stack) {
        return stack.isEmpty() ? null : Inputs.BONES.get(stack.getItem());
    }

    private static PlantSpecies petrifiedTree(ItemStack stack) {
        return stack.isEmpty() ? null : Inputs.PETRIFIED.get(stack.getItem());
    }

    public static boolean accepts(ItemStack input) {
        return !input.isEmpty() && (input.getItem() instanceof GrinderInput || boneSpecies(input) != null || input.is(JurassicContent.PLANT_FOSSIL.get())
                || input.is(JurassicContent.TWIG_FOSSIL.get()) || petrifiedTree(input) != null);
    }

    /** Chooses an output only when processing completes, so blocked machines never reroll. */
    public static ItemStack grind(ItemStack input, RandomSource random) {
        if (!input.isEmpty() && input.getItem() instanceof GrinderInput custom) return custom.grind(input, random);
        Species animal = boneSpecies(input);
        if (animal != null) {
            int roll = random.nextInt(6);
            if (roll < 3) return new ItemStack(Items.BONE_MEAL);
            if (roll < 5) return new ItemStack(Items.FLINT);
            return withInputTag(new ItemStack(GeneticsRegistry.softTissue(animal).get()), input);
        }
        PlantSpecies tree = petrifiedTree(input);
        if (tree != null) {
            int roll = random.nextInt(6);
            if (roll < 3) return new ItemStack(Items.BONE_MEAL);
            if (roll < 5) return new ItemStack(Items.FLINT);
            return withInputTag(new ItemStack(GeneticsRegistry.plantTissue(tree).get()), input);
        }
        List<PlantSpecies> candidates;
        if (input.is(JurassicContent.PLANT_FOSSIL.get())) candidates = PLANTS;
        else if (input.is(JurassicContent.TWIG_FOSSIL.get())) candidates = TREES;
        else return ItemStack.EMPTY;
        if (candidates.isEmpty()) return ItemStack.EMPTY;
        int roll = random.nextInt(4);
        if (roll < 2) return new ItemStack(Items.BONE_MEAL);
        if (roll == 2) return new ItemStack(Items.FLINT);
        PlantSpecies plant = candidates.get(random.nextInt(candidates.size()));
        return withInputTag(new ItemStack(GeneticsRegistry.plantTissue(plant).get()), input);
    }

    private static ItemStack withInputTag(ItemStack output, ItemStack input) {
        if (input.hasTag()) output.setTag(input.getTag().copy());
        return output;
    }
}
