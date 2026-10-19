package net.mystcraft.integration.registry;

import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;
import net.mystcraft.integration.platform.RegistryBridge;
import net.mystcraft.minecraft.exploration.BookDisplayMenu;

public final class BookDisplayRegistry {
    public static Supplier<MenuType<BookDisplayMenu>> DISPLAY_MENU;
    private BookDisplayRegistry() {}
    public static void register(RegistryBridge bridge) { DISPLAY_MENU = bridge.registerMenu("book_display", BookDisplayMenu::new); }
}
