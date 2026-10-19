package ports.jurassicraft.journal;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;

public final class JournalRegistry {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    public static final RegistryObject<Item> JOURNAL = ITEMS.register("ingen_journal", JournalItem::new);
    private JournalRegistry() {}
    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
