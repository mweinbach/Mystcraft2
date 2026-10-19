package net.mystcraft.integration.registry;

import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.platform.RegistryBridge;
import net.mystcraft.minecraft.item.DescriptiveBookItem;
import net.mystcraft.minecraft.writing.PageItem;

public final class AgeRegistry {
    public static Supplier<Item> PAGE;
    public static Supplier<Item> DESCRIPTIVE_BOOK;
    private AgeRegistry() {}
    public static void register(RegistryBridge bridge) {
        PAGE = bridge.registerItem("page", () -> new PageItem(new Item.Properties().tab(MystcraftItems.TAB)));
        DESCRIPTIVE_BOOK = bridge.registerItem("descriptive_book", () -> new DescriptiveBookItem(new Item.Properties().tab(MystcraftItems.TAB)));
    }
}
