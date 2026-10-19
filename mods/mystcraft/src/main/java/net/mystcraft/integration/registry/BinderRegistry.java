package net.mystcraft.integration.registry;

import java.util.function.Supplier;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.platform.RegistryBridge;
import net.mystcraft.minecraft.binder.BookBinderBlock;
import net.mystcraft.minecraft.binder.BookBinderBlockEntity;
import net.mystcraft.minecraft.binder.BookBinderMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class BinderRegistry {
    public static Supplier<Block> BOOK_BINDER;
    public static Supplier<Item> BOOK_BINDER_ITEM;
    public static Supplier<BlockEntityType<BookBinderBlockEntity>> BOOK_BINDER_ENTITY;
    public static Supplier<MenuType<BookBinderMenu>> BOOK_BINDER_MENU;
    private BinderRegistry() {}
    public static void registerBlocks(RegistryBridge bridge) {
        BOOK_BINDER = bridge.registerBlock("book_binder", BookBinderBlock::new);
    }
    public static void register(RegistryBridge bridge) {
        BOOK_BINDER_ITEM = bridge.registerItem("book_binder", () -> new BlockItem(BOOK_BINDER.get(), new Item.Properties().tab(MystcraftItems.TAB)));
        BOOK_BINDER_ENTITY = bridge.registerBlockEntityType("book_binder", () -> BlockEntityType.Builder.of(BookBinderBlockEntity::new, BOOK_BINDER.get()).build(null));
        BOOK_BINDER_MENU = bridge.registerMenu("book_binder", BookBinderMenu::new);
    }
}
