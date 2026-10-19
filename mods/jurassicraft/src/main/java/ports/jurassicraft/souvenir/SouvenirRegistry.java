package ports.jurassicraft.souvenir;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Upstream decorative gifts and collectibles, kept as ordinary items. */
public final class SouvenirRegistry {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,PortMod.MOD_ID);
    public static final RegistryObject<Item> AMBER_CANE=item("amber_cane");
    public static final RegistryObject<Item> AMBER_KEYCHAIN=item("amber_keychain");
    public static final RegistryObject<Item> MR_DNA_KEYCHAIN=item("mr_dna_keychain");
    public static final RegistryObject<Item> LUNCH_BOX=item("lunch_box");
    public static final RegistryObject<Item> STAMP_SET=item("stamp_set");
    private SouvenirRegistry() {}
    private static RegistryObject<Item> item(String id) { return ITEMS.register(id,() -> new Item(new Item.Properties().tab(JurassicContent.TAB))); }
    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
