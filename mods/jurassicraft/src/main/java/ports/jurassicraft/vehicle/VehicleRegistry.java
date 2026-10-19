package ports.jurassicraft.vehicle;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Vehicle and component registration kept separate from the shared content registry. */
public final class VehicleRegistry {
    public static final Supplier<SoundEvent> CAR_MOVE = sound("car_move");
    public static final Supplier<SoundEvent> HELICOPTER_WARNING = sound("helicopter_warning");
    public static final Supplier<EntityType<JeepEntity>> JEEP = Platform.register(Registry.ENTITY_TYPE_REGISTRY, "jeep_wrangler", () -> EntityType.Builder
            .<JeepEntity>of(JeepEntity::new, MobCategory.MISC).sized(3.0F, 2.5F).clientTrackingRange(12).updateInterval(1)
            .build(PortMod.MOD_ID + ":jeep_wrangler"));
    public static final Supplier<EntityType<ExplorerEntity>> EXPLORER = Platform.register(Registry.ENTITY_TYPE_REGISTRY, "ford_explorer", () -> EntityType.Builder
            .<ExplorerEntity>of(ExplorerEntity::new, MobCategory.MISC).sized(3.0F, 2.5F).clientTrackingRange(12).updateInterval(1)
            .build(PortMod.MOD_ID + ":ford_explorer"));
    public static final Supplier<EntityType<HelicopterEntity>> HELICOPTER = Platform.register(Registry.ENTITY_TYPE_REGISTRY, "helicopter", () -> EntityType.Builder
            .<HelicopterEntity>of(HelicopterEntity::new, MobCategory.MISC).sized(2.0F, 3.5F).clientTrackingRange(16).updateInterval(1)
            .build(PortMod.MOD_ID + ":helicopter"));
    public static final Supplier<Item> JEEP_ITEM = Platform.register(Registry.ITEM_REGISTRY, "jeep_wrangler", () -> new VehicleItem(JEEP, new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
    public static final Supplier<Item> EXPLORER_ITEM = Platform.register(Registry.ITEM_REGISTRY, "ford_explorer", () -> new VehicleItem(EXPLORER, new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
    public static final Supplier<Item> HELICOPTER_ITEM = Platform.register(Registry.ITEM_REGISTRY, "helicopter", () -> new VehicleItem(HELICOPTER, new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
    public static final Supplier<Item> CHASSIS = component("car_chassis");
    public static final Supplier<Item> SEATS = component("car_seats");
    public static final Supplier<Item> WINDSCREEN = component("car_windscreen");
    public static final Supplier<Item> ENGINE = component("engine_system");
    public static final Supplier<Item> TIRE = component("car_tire");
    public static final Supplier<Item> UNFINISHED_CAR = component("unfinished_car");

    private VehicleRegistry() {}
    private static Supplier<SoundEvent> sound(String id) {
        return Platform.register(Registry.SOUND_EVENT_REGISTRY, id, () -> new SoundEvent(new ResourceLocation(PortMod.MOD_ID, id)));
    }
    private static Supplier<Item> component(String id) { return Platform.register(Registry.ITEM_REGISTRY, id, () -> new Item(new Item.Properties().tab(JurassicContent.TAB))); }
    public static void init() {
        VehicleNetwork.register();
    }
}
