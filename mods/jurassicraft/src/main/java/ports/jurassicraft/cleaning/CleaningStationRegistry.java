package ports.jurassicraft.cleaning;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Independent registrations for the cleaning station. */
public final class CleaningStationRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PortMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PortMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, PortMod.MOD_ID);

    public static final RegistryObject<Block> BLOCK = BLOCKS.register("cleaning_station", CleaningStationBlock::new);
    public static final RegistryObject<Item> ITEM = ITEMS.register("cleaning_station",
            () -> new BlockItem(BLOCK.get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final RegistryObject<BlockEntityType<CleaningStationBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register("cleaning_station",
            () -> BlockEntityType.Builder.of(CleaningStationBlockEntity::new, BLOCK.get()).build(null));
    public static final RegistryObject<MenuType<CleaningStationMenu>> MENU = MENUS.register("cleaning_station",
            () -> IForgeMenuType.create(CleaningStationMenu::new));

    private CleaningStationRegistry() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
    }
}
