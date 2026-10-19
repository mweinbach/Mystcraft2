package ports.jurassicraft.mural;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** The original wall mural and the original, inert tracking implant item. */
public final class MuralRegistry {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, PortMod.MOD_ID);
    public static final RegistryObject<EntityType<MuralEntity>> ENTITY = ENTITIES.register("mural",
            () -> EntityType.Builder.<MuralEntity>of(MuralEntity::new, MobCategory.MISC).sized(8, 4)
                    .clientTrackingRange(12).updateInterval(10).build(PortMod.MOD_ID + ":mural"));
    public static final RegistryObject<Item> MURAL = ITEMS.register("mural", MuralItem::new);
    // Upstream registers this item but provides no use, recipe, or tracker system.
    public static final RegistryObject<Item> TRACKER = ITEMS.register("tracker", () -> new Item(new Item.Properties().tab(JurassicContent.TAB)));
    private MuralRegistry() {}
    public static void register(IEventBus bus) { ITEMS.register(bus); ENTITIES.register(bus); }
}
