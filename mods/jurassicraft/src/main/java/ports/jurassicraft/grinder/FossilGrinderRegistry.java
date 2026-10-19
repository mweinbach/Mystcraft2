package ports.jurassicraft.grinder;

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

/** Registration for the fossil grinder and its crafting components. */
public final class FossilGrinderRegistry {

    public static final Supplier<Block> FOSSIL_GRINDER = Platform.register(Registry.BLOCK_REGISTRY, "fossil_grinder", FossilGrinderBlock::new);
    public static final Supplier<Item> FOSSIL_GRINDER_ITEM = Platform.register(Registry.ITEM_REGISTRY, "fossil_grinder", () -> new BlockItem(FOSSIL_GRINDER.get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final Supplier<Item> IRON_ROD = Platform.register(Registry.ITEM_REGISTRY, "iron_rod", () -> new Item(new Item.Properties().tab(JurassicContent.TAB)));
    public static final Supplier<Item> IRON_BLADES = Platform.register(Registry.ITEM_REGISTRY, "iron_blades", () -> new Item(new Item.Properties().tab(JurassicContent.TAB)));
    public static final Supplier<BlockEntityType<FossilGrinderBlockEntity>> FOSSIL_GRINDER_ENTITY = Platform.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, "fossil_grinder", () -> BlockEntityType.Builder.of(FossilGrinderBlockEntity::new, FOSSIL_GRINDER.get()).build(null));
    public static final Supplier<MenuType<FossilGrinderMenu>> FOSSIL_GRINDER_MENU = Platform.register(Registry.MENU_REGISTRY, "fossil_grinder", () -> Platform.menuType(FossilGrinderMenu::new));

    private FossilGrinderRegistry() {}

    public static void init() {
    }
}
