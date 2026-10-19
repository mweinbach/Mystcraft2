package ports.jurassicraft.souvenir;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class SouvenirRegistry {
    private static Item amberCane, amberKeychain, mrDnaKeychain, lunchBox, stampSet;
    public static final Supplier<Item> AMBER_CANE = () -> amberCane;
    public static final Supplier<Item> AMBER_KEYCHAIN = () -> amberKeychain;
    public static final Supplier<Item> MR_DNA_KEYCHAIN = () -> mrDnaKeychain;
    public static final Supplier<Item> LUNCH_BOX = () -> lunchBox;
    public static final Supplier<Item> STAMP_SET = () -> stampSet;
    private SouvenirRegistry() {}
    private static Item item(String id) { return Registry.register(Registry.ITEM, new ResourceLocation(PortMod.MOD_ID, id), new Item(new Item.Properties().tab(JurassicContent.TAB))); }
    public static void register() {
        amberCane = item("amber_cane"); amberKeychain = item("amber_keychain"); mrDnaKeychain = item("mr_dna_keychain");
        lunchBox = item("lunch_box"); stampSet = item("stamp_set");
    }
}
