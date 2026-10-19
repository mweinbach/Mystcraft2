package ports.jurassicraft.souvenir;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Upstream decorative gifts and collectibles, kept as ordinary items. */
public final class SouvenirRegistry {
    public static final Supplier<Item> AMBER_CANE=item("amber_cane");
    public static final Supplier<Item> AMBER_KEYCHAIN=item("amber_keychain");
    public static final Supplier<Item> MR_DNA_KEYCHAIN=item("mr_dna_keychain");
    public static final Supplier<Item> LUNCH_BOX=item("lunch_box");
    public static final Supplier<Item> STAMP_SET=item("stamp_set");
    private SouvenirRegistry() {}
    private static Supplier<Item> item(String id) { return Platform.register(Registry.ITEM_REGISTRY, id,() -> new Item(new Item.Properties().tab(JurassicContent.TAB))); }
    public static void init() { }
}
