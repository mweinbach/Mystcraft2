package ports.jurassicraft.plantculture;

import net.minecraft.world.item.ItemStack;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.plants.PlantRegistry;
import ports.jurassicraft.plants.PlantSpecies;

public final class PlantCulture {
    private PlantCulture() {}
    /** Convert complete plant DNA into a callus, preserving the complete sample tag. */
    public static ItemStack createCallus(ItemStack sample) {
        var data = Genetics.read(sample.getTag());
        if (data.isEmpty() || data.get().kind() != DNAData.Kind.PLANT || data.get().quality() != 100) return ItemStack.EMPTY;
        PlantSpecies species = data.get().plant();
        if (!sample.is(GeneticsRegistry.plantDna(species).get()) || PlantRegistry.plant(species) == null) return ItemStack.EMPTY;
        ItemStack output = new ItemStack(PlantCultureRegistry.callus(species).get());
        output.setTag(sample.getTag().copy());
        return output;
    }
}
