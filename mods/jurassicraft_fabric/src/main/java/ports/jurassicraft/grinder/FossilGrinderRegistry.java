package ports.jurassicraft.grinder;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class FossilGrinderRegistry {
    private static Block grinder;
    private static Item grinderItem, rod, blades;
    private static BlockEntityType<FossilGrinderBlockEntity> entity;
    private static MenuType<FossilGrinderMenu> menu;
    public static final Supplier<Block> FOSSIL_GRINDER = () -> grinder;
    public static final Supplier<Item> FOSSIL_GRINDER_ITEM = () -> grinderItem;
    public static final Supplier<Item> IRON_ROD = () -> rod;
    public static final Supplier<Item> IRON_BLADES = () -> blades;
    public static final Supplier<BlockEntityType<FossilGrinderBlockEntity>> FOSSIL_GRINDER_ENTITY = () -> entity;
    public static final Supplier<MenuType<FossilGrinderMenu>> FOSSIL_GRINDER_MENU = () -> menu;
    private FossilGrinderRegistry() {}
    private static ResourceLocation id(String name) { return new ResourceLocation(PortMod.MOD_ID, name); }
    public static void register() {
        grinder = Registry.register(Registry.BLOCK, id("fossil_grinder"), new FossilGrinderBlock());
        grinderItem = Registry.register(Registry.ITEM, id("fossil_grinder"), new BlockItem(grinder, new Item.Properties().tab(JurassicContent.TAB)));
        rod = Registry.register(Registry.ITEM, id("iron_rod"), new Item(new Item.Properties().tab(JurassicContent.TAB)));
        blades = Registry.register(Registry.ITEM, id("iron_blades"), new Item(new Item.Properties().tab(JurassicContent.TAB)));
        entity = Registry.register(Registry.BLOCK_ENTITY_TYPE, id("fossil_grinder"), BlockEntityType.Builder.of(FossilGrinderBlockEntity::new, grinder).build(null));
        menu = ScreenHandlerRegistry.registerExtended(id("fossil_grinder"), FossilGrinderMenu::new);
    }
}
