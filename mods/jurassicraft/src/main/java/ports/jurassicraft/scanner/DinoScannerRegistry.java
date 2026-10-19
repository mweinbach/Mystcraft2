package ports.jurassicraft.scanner;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class DinoScannerRegistry {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    public static final RegistryObject<Item> SCANNER = ITEMS.register("dino_scanner",
            () -> new DinoScannerItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
    private DinoScannerRegistry() {}
    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
