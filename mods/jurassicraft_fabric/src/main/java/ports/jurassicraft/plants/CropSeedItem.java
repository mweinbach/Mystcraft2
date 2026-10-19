package ports.jurassicraft.plants;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

final class CropSeedItem extends BlockItem {
    private final String name;
    CropSeedItem(Block crop, Properties properties, String name) { super(crop, properties); this.name = name; }
    @Override public String getDescriptionId() { return "item.jurassicraft." + name; }
}
