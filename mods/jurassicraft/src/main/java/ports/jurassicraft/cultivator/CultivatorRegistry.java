package ports.jurassicraft.cultivator;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class CultivatorRegistry {
    public static final Supplier<Block> CULTIVATOR=Platform.register(Registry.BLOCK_REGISTRY, "cultivate_bottom",CultivatorBlock::new);
    public static final Supplier<Item> ITEM=Platform.register(Registry.ITEM_REGISTRY, "cultivate_bottom",() -> new BlockItem(CULTIVATOR.get(),new Item.Properties().tab(JurassicContent.TAB)));
    public static final Supplier<BlockEntityType<CultivatorBlockEntity>> ENTITY=Platform.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, "cultivate_bottom",() -> BlockEntityType.Builder.of(CultivatorBlockEntity::new,CULTIVATOR.get()).build(null));
    public static final Supplier<MenuType<CultivatorMenu>> MENU=Platform.register(Registry.MENU_REGISTRY, "cultivator",() -> Platform.menuType(CultivatorMenu::new));
    private CultivatorRegistry() {}
    public static void init() { }
}
