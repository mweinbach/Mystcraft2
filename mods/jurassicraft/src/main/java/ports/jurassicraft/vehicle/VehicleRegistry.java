package ports.jurassicraft.vehicle;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Vehicle and component registration kept separate from the shared content registry. */
public final class VehicleRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, PortMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, PortMod.MOD_ID);
    public static final RegistryObject<SoundEvent> CAR_MOVE = sound("car_move");
    public static final RegistryObject<SoundEvent> HELICOPTER_WARNING = sound("helicopter_warning");
    public static final RegistryObject<EntityType<JeepEntity>> JEEP = ENTITIES.register("jeep_wrangler", () -> EntityType.Builder
            .<JeepEntity>of(JeepEntity::new, MobCategory.MISC).sized(3.0F, 2.5F).clientTrackingRange(12).updateInterval(1)
            .build(PortMod.MOD_ID + ":jeep_wrangler"));
    public static final RegistryObject<EntityType<ExplorerEntity>> EXPLORER = ENTITIES.register("ford_explorer", () -> EntityType.Builder
            .<ExplorerEntity>of(ExplorerEntity::new, MobCategory.MISC).sized(3.0F, 2.5F).clientTrackingRange(12).updateInterval(1)
            .build(PortMod.MOD_ID + ":ford_explorer"));
    public static final RegistryObject<EntityType<HelicopterEntity>> HELICOPTER = ENTITIES.register("helicopter", () -> EntityType.Builder
            .<HelicopterEntity>of(HelicopterEntity::new, MobCategory.MISC).sized(2.0F, 3.5F).clientTrackingRange(16).updateInterval(1)
            .build(PortMod.MOD_ID + ":helicopter"));
    public static final RegistryObject<Item> JEEP_ITEM = ITEMS.register("jeep_wrangler", () -> new VehicleItem(JEEP, new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
    public static final RegistryObject<Item> EXPLORER_ITEM = ITEMS.register("ford_explorer", () -> new VehicleItem(EXPLORER, new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
    public static final RegistryObject<Item> HELICOPTER_ITEM = ITEMS.register("helicopter", () -> new VehicleItem(HELICOPTER, new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
    public static final RegistryObject<Item> CHASSIS = component("car_chassis");
    public static final RegistryObject<Item> SEATS = component("car_seats");
    public static final RegistryObject<Item> WINDSCREEN = component("car_windscreen");
    public static final RegistryObject<Item> ENGINE = component("engine_system");
    public static final RegistryObject<Item> TIRE = component("car_tire");
    public static final RegistryObject<Item> UNFINISHED_CAR = component("unfinished_car");

    private VehicleRegistry() {}
    private static RegistryObject<SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> new SoundEvent(new ResourceLocation(PortMod.MOD_ID, id)));
    }
    private static RegistryObject<Item> component(String id) { return ITEMS.register(id, () -> new Item(new Item.Properties().tab(JurassicContent.TAB))); }
    public static void register(IEventBus bus) {
        VehicleConfig.register();
        ENTITIES.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        VehicleNetwork.register();
    }
}
