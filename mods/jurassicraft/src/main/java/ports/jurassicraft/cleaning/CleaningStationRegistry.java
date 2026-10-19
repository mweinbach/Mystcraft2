package ports.jurassicraft.cleaning;

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

/** Independent registrations for the cleaning station. */
public final class CleaningStationRegistry {

    public static final Supplier<Block> BLOCK = Platform.register(Registry.BLOCK_REGISTRY, "cleaning_station", CleaningStationBlock::new);
    public static final Supplier<Item> ITEM = Platform.register(Registry.ITEM_REGISTRY, "cleaning_station",
            () -> new BlockItem(BLOCK.get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final Supplier<BlockEntityType<CleaningStationBlockEntity>> BLOCK_ENTITY = Platform.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, "cleaning_station",
            () -> BlockEntityType.Builder.of(CleaningStationBlockEntity::new, BLOCK.get()).build(null));
    public static final Supplier<MenuType<CleaningStationMenu>> MENU = Platform.register(Registry.MENU_REGISTRY, "cleaning_station",
            () -> Platform.menuType(CleaningStationMenu::new));

    private CleaningStationRegistry() {}

    public static void init() {
    }
}
