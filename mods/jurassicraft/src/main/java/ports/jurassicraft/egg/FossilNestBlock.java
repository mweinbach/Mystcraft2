package ports.jurassicraft.egg;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import ports.jurassicraft.cleaning.Cleanable;

/** One of the three original nest variants, either raw or wrapped in plaster. */
public final class FossilNestBlock extends Block implements Cleanable {
    private final int variant;
    private final boolean encased;

    public FossilNestBlock(int variant, boolean encased) {
        super(BlockBehaviour.Properties.of(Material.STONE).strength(1.5F).sound(SoundType.STONE)
                .requiresCorrectToolForDrops());
        this.variant = variant;
        this.encased = encased;
    }

    public int variant() { return variant; }
    public boolean encased() { return encased; }

    @Override
    public boolean isCleanable(ItemStack input) {
        return encased && input.is(asItem());
    }

    @Override
    public ItemStack clean(ItemStack input, RandomSource random) {
        if (!isCleanable(input)) return ItemStack.EMPTY;
        if (random.nextBoolean()) {
            return new ItemStack(FossilizedEggRegistry.variant(variant).get(), random.nextInt(7) + 1);
        }
        return new ItemStack(random.nextBoolean() ? Items.FLINT : Items.BONE_MEAL);
    }
}
