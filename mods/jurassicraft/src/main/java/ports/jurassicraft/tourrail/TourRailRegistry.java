package ports.jurassicraft.tourrail;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class TourRailRegistry {
    public static final Supplier<Block> RAIL = rail("tour_rail", 0.125, 0xffffff);
    public static final Supplier<Block> SLOW = rail("tour_rail_slow", 0.0625, 0xa80000);
    public static final Supplier<Block> MEDIUM = rail("tour_rail_medium", 0.125, 0xff7600);
    public static final Supplier<Block> FAST = rail("tour_rail_fast", 0.25, 0x00a800);
    public static final Supplier<BlockEntityType<TourRailBlockEntity>> RENDER_ENTITY = Platform.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, "tour_rail",
            () -> BlockEntityType.Builder.of(TourRailBlockEntity::new, RAIL.get(), SLOW.get(), MEDIUM.get(), FAST.get()).build(null));
    private TourRailRegistry() {}
    private static Supplier<Block> rail(String name, double speed, int color) {
        Supplier<Block> block = Platform.register(Registry.BLOCK_REGISTRY, name, () -> new TourRailBlock(speed, color));
        Platform.register(Registry.ITEM_REGISTRY, name, () -> new BlockItem(block.get(), new Item.Properties().tab(JurassicContent.TAB)));
        return block;
    }
    public static void init() { }
}
