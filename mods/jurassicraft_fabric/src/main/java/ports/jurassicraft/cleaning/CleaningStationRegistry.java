package ports.jurassicraft.cleaning;

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

public final class CleaningStationRegistry {
    private static Block block;
    private static Item item;
    private static BlockEntityType<CleaningStationBlockEntity> blockEntity;
    private static MenuType<CleaningStationMenu> menu;
    public static final Supplier<Block> BLOCK = () -> block;
    public static final Supplier<Item> ITEM = () -> item;
    public static final Supplier<BlockEntityType<CleaningStationBlockEntity>> BLOCK_ENTITY = () -> blockEntity;
    public static final Supplier<MenuType<CleaningStationMenu>> MENU = () -> menu;
    private CleaningStationRegistry() {}
    public static void register() {
        ResourceLocation id = new ResourceLocation(PortMod.MOD_ID, "cleaning_station");
        block = Registry.register(Registry.BLOCK, id, new CleaningStationBlock());
        item = Registry.register(Registry.ITEM, id, new BlockItem(block, new Item.Properties().tab(JurassicContent.TAB)));
        blockEntity = Registry.register(Registry.BLOCK_ENTITY_TYPE, id, BlockEntityType.Builder.of(CleaningStationBlockEntity::new, block).build(null));
        menu = ScreenHandlerRegistry.registerExtended(id, CleaningStationMenu::new);
    }
}
