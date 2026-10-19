package ports.jurassicraft.egg;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.grinder.GrinderInput;

/** A fossilized egg is one of the three visual variants from a fossil nest. */
public final class FossilizedEggItem extends Item implements GrinderInput {
    private static final Species[] PREHISTORIC_SPECIES = Species.values();

    public FossilizedEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack grind(ItemStack input, RandomSource random) {
        if (input.isEmpty() || input.getItem() != this) return ItemStack.EMPTY;
        int roll = random.nextInt(3);
        if (roll == 1) return new ItemStack(Items.BONE_MEAL);
        if (roll == 2) return new ItemStack(Items.FLINT);
        Species species = PREHISTORIC_SPECIES[random.nextInt(PREHISTORIC_SPECIES.length)];
        ItemStack tissue = new ItemStack(GeneticsRegistry.softTissue(species).get());
        // Upstream preserves the egg's complete NBT on tissue, and does not
        // transfer it to bone meal or flint. Copy to avoid aliasing the input.
        if (input.hasTag()) tissue.setTag(input.getTag().copy());
        return tissue;
    }
}
