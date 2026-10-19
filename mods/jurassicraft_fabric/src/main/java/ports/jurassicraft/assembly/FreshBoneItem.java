package ports.jurassicraft.assembly;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.genetics.GeneticsItem;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.grinder.GrinderInput;

/** Fresh bones always produce soft tissue when ground, retaining the animal's DNA. */
public class FreshBoneItem extends GeneticsItem implements GrinderInput {
    private final Species species;
    public FreshBoneItem(Species species, Properties properties) {
        super(properties);
        this.species = species;
    }

    @Override public ItemStack grind(ItemStack input, RandomSource random) {
        ItemStack result = new ItemStack(GeneticsRegistry.softTissue(species).get());
        if (input.hasTag()) result.setTag(input.getTag().copy());
        return result;
    }
}
