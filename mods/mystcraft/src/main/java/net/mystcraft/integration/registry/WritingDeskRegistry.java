package net.mystcraft.integration.registry;

import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;
import net.mystcraft.minecraft.writing.WritingDeskItem;
import net.mystcraft.minecraft.writing.PageCollectionMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.platform.RegistryBridge;
import net.mystcraft.minecraft.writing.PageCollectionItem;
import net.mystcraft.minecraft.writing.WritingDeskBlock;
import net.mystcraft.minecraft.writing.WritingDeskBlockEntity;
import net.mystcraft.minecraft.writing.WritingDeskMenu;

public final class WritingDeskRegistry {
    public static Supplier<Block> WRITING_DESK;
    public static Supplier<Item> WRITING_DESK_ITEM, WRITING_DESK_EXTENSION, FOLDER, PORTFOLIO;
    public static Supplier<BlockEntityType<WritingDeskBlockEntity>> WRITING_DESK_ENTITY;
    public static Supplier<MenuType<WritingDeskMenu>> WRITING_DESK_MENU;
    public static Supplier<MenuType<PageCollectionMenu>> PAGE_COLLECTION_MENU;
    private WritingDeskRegistry() {}
    public static void registerBlocks(RegistryBridge bridge) { WRITING_DESK = bridge.registerBlock("writing_desk", WritingDeskBlock::new); }
    public static void register(RegistryBridge bridge) {
        WRITING_DESK_ITEM = bridge.registerItem("writing_desk", () -> new WritingDeskItem(new Item.Properties().tab(MystcraftItems.TAB), false));
        WRITING_DESK_EXTENSION = bridge.registerItem("writing_desk_extension", () -> new WritingDeskItem(new Item.Properties().tab(MystcraftItems.TAB), true));
        FOLDER = bridge.registerItem("folder", () -> new PageCollectionItem(new Item.Properties().tab(MystcraftItems.TAB), true));
        PORTFOLIO = bridge.registerItem("portfolio", () -> new PageCollectionItem(new Item.Properties().tab(MystcraftItems.TAB), false));
        WRITING_DESK_ENTITY = bridge.registerBlockEntityType("writing_desk", () -> BlockEntityType.Builder.of(WritingDeskBlockEntity::new, WRITING_DESK.get()).build(null));
        WRITING_DESK_MENU = bridge.registerMenu("writing_desk", WritingDeskMenu::new);
        PAGE_COLLECTION_MENU = bridge.registerMenu("page_collection", PageCollectionMenu::new);
    }
}
