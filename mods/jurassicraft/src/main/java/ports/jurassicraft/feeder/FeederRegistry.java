package ports.jurassicraft.feeder;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.platform.Platform;

public final class FeederRegistry {

    public static final Supplier<Block> BLOCK = Platform.register(Registry.BLOCK_REGISTRY, "feeder", FeederBlock::new);
    public static final Supplier<Item> ITEM = Platform.register(Registry.ITEM_REGISTRY, "feeder", () -> new BlockItem(BLOCK.get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final Supplier<BlockEntityType<FeederBlockEntity>> BLOCK_ENTITY = Platform.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, "feeder", () -> BlockEntityType.Builder.of(FeederBlockEntity::new, BLOCK.get()).build(null));
    public static final Supplier<MenuType<FeederMenu>> MENU = Platform.menu("feeder", (id, inventory, data) -> new FeederMenu(id, inventory));

    private FeederRegistry() {}

    public static void init() {
    }
}
