package ports.jurassicraft.fieldguide;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class FieldGuideRegistry {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    public static final RegistryObject<Item> FIELD_GUIDE = ITEMS.register("field_guide",
            () -> new FieldGuideItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));

    private FieldGuideRegistry() {}
    public static void register(IEventBus bus) { ITEMS.register(bus); FieldGuideNetwork.register(); }
}
