package ports.jurassicraft.feeder;

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

public final class FeederRegistry {
    private static final ResourceLocation ID=new ResourceLocation(PortMod.MOD_ID,"feeder");
    public static final Supplier<Block> BLOCK=block();
    public static final Supplier<Item> ITEM=item();
    public static final Supplier<BlockEntityType<FeederBlockEntity>> BLOCK_ENTITY=entity();
    public static final Supplier<MenuType<FeederMenu>> MENU=menu();
    private FeederRegistry() {}
    private static Supplier<Block> block() { Block value=Registry.register(Registry.BLOCK,ID,new FeederBlock()); return () -> value; }
    private static Supplier<Item> item() { Item value=Registry.register(Registry.ITEM,ID,new BlockItem(BLOCK.get(),new Item.Properties().tab(JurassicContent.TAB))); return () -> value; }
    private static Supplier<BlockEntityType<FeederBlockEntity>> entity() { BlockEntityType<FeederBlockEntity> value=Registry.register(Registry.BLOCK_ENTITY_TYPE,ID,BlockEntityType.Builder.of(FeederBlockEntity::new,BLOCK.get()).build(null)); return () -> value; }
    private static Supplier<MenuType<FeederMenu>> menu() { MenuType<FeederMenu> value=ScreenHandlerRegistry.registerSimple(ID,FeederMenu::new); return () -> value; }
    public static void init() {}
}
