package net.mystcraft.integration;

import net.mystcraft.integration.network.LinkingNetwork;
import net.mystcraft.minecraft.item.LinkingBookItem;
import net.mystcraft.minecraft.writing.PageItem;
import net.mystcraft.minecraft.item.UnlinkedLinkBookItem;
import net.mystcraft.minecraft.recipe.LinkingBookRecipe;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.mystcraft.integration.registry.BookstandRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;
import java.util.function.Supplier;
import net.mystcraft.integration.platform.RegistryBridge;

/** Shared classic linking-book and booster item registrations. */
public final class MystcraftItems {
    public static CreativeModeTab TAB;
    public static Supplier<Item> LINKING_PANEL;
    public static Supplier<Item> BOOSTER;
    public static Supplier<Item> LINKING_BOOK;
    public static Supplier<Item> UNLINKED_LINK_BOOK;
    public static Supplier<SimpleRecipeSerializer<LinkingBookRecipe>> LINKING_BOOK_RECIPE;
    public static Supplier<Item> BOOKSTAND;

    private MystcraftItems() {}
    public static void register(RegistryBridge bridge) {
        TAB = bridge.createCreativeTab("mystcraft", () -> new ItemStack(LINKING_BOOK.get()));
        BOOSTER = bridge.registerItem("booster", () -> new net.mystcraft.minecraft.item.BoosterPackItem(new Item.Properties().tab(TAB)));
        LINKING_PANEL = bridge.registerItem("linking_panel", () -> new PageItem(new Item.Properties().tab(TAB)));
        LINKING_BOOK = bridge.registerItem("linking_book", () -> new LinkingBookItem(new Item.Properties().tab(TAB), (player, hand, book) -> LinkingNetwork.openBook(player, hand)));
        UNLINKED_LINK_BOOK = bridge.registerItem("unlinked_link_book", () -> new UnlinkedLinkBookItem(new Item.Properties().tab(TAB), LINKING_BOOK));
        LINKING_BOOK_RECIPE = bridge.registerRecipeSerializer("linking_book", () -> new SimpleRecipeSerializer<>(LinkingBookRecipe::new));
        BOOKSTAND = bridge.registerItem("bookstand", () -> new BlockItem(BookstandRegistry.BOOKSTAND.get(), new Item.Properties().tab(TAB)));
    }
}
