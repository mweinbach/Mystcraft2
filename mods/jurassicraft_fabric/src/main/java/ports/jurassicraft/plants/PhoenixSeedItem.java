package ports.jurassicraft.plants;

import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Planting seeds must not replace the sapling's normal block-item mapping. */
final class PhoenixSeedItem extends BlockItem {
    PhoenixSeedItem(Block block, Properties properties) { super(block, properties); }
    @Override
    public void registerBlocks(Map<Block, Item> blocks, Item item) {}
    @Override
    public String getDescriptionId() { return "item.jurassicraft.phoenix_seeds"; }
}
