package net.mystcraft.integration.registry;

import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.platform.RegistryBridge;
import net.mystcraft.minecraft.portal.BookReceptacleBlock;
import net.mystcraft.minecraft.portal.BookReceptacleBlockEntity;
import net.mystcraft.minecraft.portal.CrystalBlock;
import net.mystcraft.minecraft.portal.LinkPortalBlock;

public final class PortalRegistry {
    public static Supplier<Block> CRYSTAL, BOOK_RECEPTACLE, LINK_PORTAL;
    public static Supplier<Item> CRYSTAL_ITEM, BOOK_RECEPTACLE_ITEM;
    public static Supplier<BlockEntityType<BookReceptacleBlockEntity>> RECEPTACLE_ENTITY;
    private PortalRegistry() {}
    public static void registerBlocks(RegistryBridge bridge) {
        CRYSTAL = bridge.registerBlock("crystal", CrystalBlock::new);
        BOOK_RECEPTACLE = bridge.registerBlock("book_receptacle", BookReceptacleBlock::new);
        LINK_PORTAL = bridge.registerBlock("link_portal", LinkPortalBlock::new);
    }
    public static void register(RegistryBridge bridge) {
        CRYSTAL_ITEM = bridge.registerItem("crystal", () -> new BlockItem(CRYSTAL.get(), new Item.Properties().tab(MystcraftItems.TAB)));
        BOOK_RECEPTACLE_ITEM = bridge.registerItem("book_receptacle", () -> new BlockItem(BOOK_RECEPTACLE.get(), new Item.Properties().tab(MystcraftItems.TAB)));
        RECEPTACLE_ENTITY = bridge.registerBlockEntityType("book_receptacle", () -> BlockEntityType.Builder.of(
                BookReceptacleBlockEntity::new, BOOK_RECEPTACLE.get()).build(null));
    }
}
