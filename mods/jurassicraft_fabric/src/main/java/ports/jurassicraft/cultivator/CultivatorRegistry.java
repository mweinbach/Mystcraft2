package ports.jurassicraft.cultivator;

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

public final class CultivatorRegistry {
    public static final Supplier<Block> CULTIVATOR=hold(Registry.register(Registry.BLOCK,id("cultivate_bottom"),new CultivatorBlock()));
    public static final Supplier<Item> ITEM=hold(Registry.register(Registry.ITEM,id("cultivate_bottom"),new BlockItem(CULTIVATOR.get(),new Item.Properties().tab(JurassicContent.TAB))));
    public static final Supplier<BlockEntityType<CultivatorBlockEntity>> ENTITY=hold(Registry.register(Registry.BLOCK_ENTITY_TYPE,id("cultivate_bottom"),BlockEntityType.Builder.of(CultivatorBlockEntity::new,CULTIVATOR.get()).build(null)));
    public static final Supplier<MenuType<CultivatorMenu>> MENU=hold(ScreenHandlerRegistry.registerExtended(id("cultivator"),CultivatorMenu::new));
    private CultivatorRegistry() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID,path); }
    private static <T> Supplier<T> hold(T value) { return () -> value; }
    public static void init() {}
}
