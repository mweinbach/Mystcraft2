package ports.jurassicraft.grinder;

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

/** Registration for the fossil grinder and its crafting components. */
public final class FossilGrinderRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PortMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PortMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, PortMod.MOD_ID);

    public static final RegistryObject<Block> FOSSIL_GRINDER = BLOCKS.register("fossil_grinder", FossilGrinderBlock::new);
    public static final RegistryObject<Item> FOSSIL_GRINDER_ITEM = ITEMS.register("fossil_grinder", () -> new BlockItem(FOSSIL_GRINDER.get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final RegistryObject<Item> IRON_ROD = ITEMS.register("iron_rod", () -> new Item(new Item.Properties().tab(JurassicContent.TAB)));
    public static final RegistryObject<Item> IRON_BLADES = ITEMS.register("iron_blades", () -> new Item(new Item.Properties().tab(JurassicContent.TAB)));
    public static final RegistryObject<BlockEntityType<FossilGrinderBlockEntity>> FOSSIL_GRINDER_ENTITY = BLOCK_ENTITIES.register("fossil_grinder", () -> BlockEntityType.Builder.of(FossilGrinderBlockEntity::new, FOSSIL_GRINDER.get()).build(null));
    public static final RegistryObject<MenuType<FossilGrinderMenu>> FOSSIL_GRINDER_MENU = MENUS.register("fossil_grinder", () -> IForgeMenuType.create(FossilGrinderMenu::new));

    private FossilGrinderRegistry() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
    }
}
