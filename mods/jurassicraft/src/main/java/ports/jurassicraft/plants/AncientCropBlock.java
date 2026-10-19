package ports.jurassicraft.plants;

import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

final class AncientCropBlock extends CropBlock {
    private final int maxAge;
    private final Supplier<Item> seed;
    AncientCropBlock(int maxAge, Supplier<Item> seed) {
        super(BlockBehaviour.Properties.copy(Blocks.WHEAT));
        this.maxAge = maxAge;
        this.seed = seed;
    }
    @Override public int getMaxAge() { return maxAge; }
    @Override protected ItemLike getBaseSeedId() { return seed.get(); }
}
