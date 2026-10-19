package ports.jurassicraft.hatching;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import ports.jurassicraft.components.ComponentsRegistry;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.GeneticsRegistry;

/** Strictly match genetic identity to its item before copying it to the next stage. */
public final class HatcheryRecipes {
    private HatcheryRecipes() {}
    public static ItemStack embryonic(ItemStack tube, ItemStack dish, ItemStack syringe) {
        if (!syringe.is(ComponentsRegistry.EMPTY_SYRINGE.get())) return ItemStack.EMPTY;
        var data = Genetics.read(tube.getTag());
        if (data.isEmpty()) return ItemStack.EMPTY;
        DNAData dna = data.get();
        ItemStack result;
        if (dna.kind() == DNAData.Kind.DINOSAUR && tube.is(GeneticsRegistry.dinosaurDna(dna.dinosaur()).get()) && dish.is(ComponentsRegistry.PETRI_DISH.get())) {
            result = new ItemStack(HatcheryRegistry.syringe(dna.dinosaur()));
        } else if (dna.kind() == DNAData.Kind.PLANT && tube.is(GeneticsRegistry.plantDna(dna.plant()).get()) && dish.is(ComponentsRegistry.PLANT_CELLS_PETRI_DISH.get())) {
            return ports.jurassicraft.plantculture.PlantCulture.createCallus(tube);
        } else return ItemStack.EMPTY;
        result.setTag(tube.getTag().copy());
        return result;
    }
    public static ItemStack calcify(ItemStack syringe, ItemStack chickenEgg) {
        if (!chickenEgg.is(Items.EGG)) return ItemStack.EMPTY;
        var data = Genetics.read(syringe.getTag());
        if (data.isEmpty() || data.get().kind() != DNAData.Kind.DINOSAUR) return ItemStack.EMPTY;
        var species = data.get().dinosaur();
        if (!syringe.is(HatcheryRegistry.syringe(species)) || HatcheryRegistry.egg(species) == null) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(HatcheryRegistry.egg(species));
        result.setTag(syringe.getTag().copy());
        return result;
    }
    public static ItemStack incubate(ItemStack egg, int temperature) {
        var data = Genetics.read(egg.getTag());
        if (data.isEmpty() || data.get().kind() != DNAData.Kind.DINOSAUR) return ItemStack.EMPTY;
        var species = data.get().dinosaur();
        if (HatcheryRegistry.egg(species) == null || !egg.is(HatcheryRegistry.egg(species))) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(HatcheryRegistry.hatchedEgg(species));
        result.setTag(egg.getTag().copy());
        result.getTag().putBoolean("Gender", temperature > 50);
        return result;
    }
    public static boolean accepts(HatcheryRegistry.Machine machine, int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (machine == HatcheryRegistry.Machine.INCUBATOR) {
            if (slot == 5) return isEnvironment(stack);
            return slot >= 0 && slot < 5 && !incubate(stack, 0).isEmpty();
        }
        if (machine == HatcheryRegistry.Machine.CALCIFIER) {
            if (slot == 1) return stack.is(Items.EGG);
            return slot == 0 && !calcify(stack, new ItemStack(Items.EGG)).isEmpty();
        }
        if (slot == 2) return stack.is(ComponentsRegistry.EMPTY_SYRINGE.get());
        if (slot == 1) return stack.is(ComponentsRegistry.PETRI_DISH.get()) || stack.is(ComponentsRegistry.PLANT_CELLS_PETRI_DISH.get());
        if (slot != 0) return false;
        var data = Genetics.read(stack.getTag());
        if (data.isEmpty()) return false;
        DNAData dna = data.get();
        return dna.kind() == DNAData.Kind.DINOSAUR ? stack.is(GeneticsRegistry.dinosaurDna(dna.dinosaur()).get())
                : stack.is(GeneticsRegistry.plantDna(dna.plant()).get());
    }

    public static boolean isEnvironment(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(HatcheryRegistry.PEAT_MOSS_ITEM.get()) || stack.getItem() instanceof IncubatorEnvironment
                || stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof IncubatorEnvironment;
    }
}
