package ports.jurassicraft.cultivator;

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

public final class CultivatorRegistry {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,PortMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,PortMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,PortMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,PortMod.MOD_ID);
    public static final RegistryObject<Block> CULTIVATOR=BLOCKS.register("cultivate_bottom",CultivatorBlock::new);
    public static final RegistryObject<Item> ITEM=ITEMS.register("cultivate_bottom",() -> new BlockItem(CULTIVATOR.get(),new Item.Properties().tab(JurassicContent.TAB)));
    public static final RegistryObject<BlockEntityType<CultivatorBlockEntity>> ENTITY=BLOCK_ENTITIES.register("cultivate_bottom",() -> BlockEntityType.Builder.of(CultivatorBlockEntity::new,CULTIVATOR.get()).build(null));
    public static final RegistryObject<MenuType<CultivatorMenu>> MENU=MENUS.register("cultivator",() -> IForgeMenuType.create(CultivatorMenu::new));
    private CultivatorRegistry() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); MENUS.register(bus); }
}
