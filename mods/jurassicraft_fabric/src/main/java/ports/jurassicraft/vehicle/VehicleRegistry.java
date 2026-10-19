package ports.jurassicraft.vehicle;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Vehicles are registered on the common Fabric initializer. */
public final class VehicleRegistry {
    /** A late-bound supplier allows items and entities to refer to one another. */
    public static final class Entry<T> implements Supplier<T> {
        private T value;
        private void set(T value) { this.value = Objects.requireNonNull(value); }
        @Override public T get() { return Objects.requireNonNull(value, "Vehicle registry not initialized"); }
    }
    public static final Entry<SoundEvent> CAR_MOVE = new Entry<>(), HELICOPTER_WARNING = new Entry<>();
    public static final Entry<EntityType<JeepEntity>> JEEP = new Entry<>();
    public static final Entry<EntityType<ExplorerEntity>> EXPLORER = new Entry<>();
    public static final Entry<EntityType<HelicopterEntity>> HELICOPTER = new Entry<>();
    public static final Entry<Item> JEEP_ITEM = new Entry<>(), EXPLORER_ITEM = new Entry<>(), HELICOPTER_ITEM = new Entry<>();
    public static final Entry<Item> CHASSIS = new Entry<>(), SEATS = new Entry<>(), WINDSCREEN = new Entry<>(),
            ENGINE = new Entry<>(), TIRE = new Entry<>(), UNFINISHED_CAR = new Entry<>();

    private VehicleRegistry() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID, path); }
    private static void item(Entry<Item> entry, String path, Item item) { entry.set(Registry.register(Registry.ITEM, id(path), item)); }
    private static void component(Entry<Item> entry, String path) {
        item(entry, path, new Item(new Item.Properties().tab(JurassicContent.TAB)));
    }
    public static void init() {
        VehicleConfig.init();
        CAR_MOVE.set(Registry.register(Registry.SOUND_EVENT, id("car_move"), new SoundEvent(id("car_move"))));
        HELICOPTER_WARNING.set(Registry.register(Registry.SOUND_EVENT, id("helicopter_warning"), new SoundEvent(id("helicopter_warning"))));
        JEEP.set(Registry.register(Registry.ENTITY_TYPE, id("jeep_wrangler"), EntityType.Builder
                .<JeepEntity>of(JeepEntity::new, MobCategory.MISC).sized(3, 2.5F).clientTrackingRange(12).updateInterval(1).build(id("jeep_wrangler").toString())));
        EXPLORER.set(Registry.register(Registry.ENTITY_TYPE, id("ford_explorer"), EntityType.Builder
                .<ExplorerEntity>of(ExplorerEntity::new, MobCategory.MISC).sized(3, 2.5F).clientTrackingRange(12).updateInterval(1).build(id("ford_explorer").toString())));
        HELICOPTER.set(Registry.register(Registry.ENTITY_TYPE, id("helicopter"), EntityType.Builder
                .<HelicopterEntity>of(HelicopterEntity::new, MobCategory.MISC).sized(2, 3.5F).clientTrackingRange(16).updateInterval(1).build(id("helicopter").toString())));
        item(JEEP_ITEM, "jeep_wrangler", new VehicleItem(JEEP, new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
        item(EXPLORER_ITEM, "ford_explorer", new VehicleItem(EXPLORER, new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
        item(HELICOPTER_ITEM, "helicopter", new VehicleItem(HELICOPTER, new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
        component(CHASSIS, "car_chassis"); component(SEATS, "car_seats"); component(WINDSCREEN, "car_windscreen");
        component(ENGINE, "engine_system"); component(TIRE, "car_tire"); component(UNFINISHED_CAR, "unfinished_car");
        VehicleNetwork.init();
    }
}
