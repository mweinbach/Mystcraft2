package ports.jurassicraft.entity;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import ports.jurassicraft.JurassicEvents;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.genetics.DinosaurAttributes;
import ports.jurassicraft.genetics.GeneticsItem;
import ports.jurassicraft.platform.Platform;

/** Registered species have separate entity IDs, but share the same base implementation. */
public final class DinosaurRegistry {
    public static final Supplier<MenuType<DinosaurOrderMenu>> ORDER_MENU = Platform.register(Registry.MENU_REGISTRY, "dinosaur_order", () -> Platform.menuType(DinosaurOrderMenu::new));
    public static final Supplier<EntityType<DinosaurEggEntity>> EGG = Platform.register(Registry.ENTITY_TYPE_REGISTRY, "dinosaur_egg", () -> EntityType.Builder
            .<DinosaurEggEntity>of(DinosaurEggEntity::new, MobCategory.MISC).sized(.3f, .5f).clientTrackingRange(8)
            .build(PortMod.MOD_ID + ":dinosaur_egg"));
    public static final Supplier<EntityType<VenomEntity>> VENOM = Platform.register(Registry.ENTITY_TYPE_REGISTRY, "venom", () -> EntityType.Builder
            .<VenomEntity>of(VenomEntity::new, MobCategory.MISC).sized(.25f, .25f).clientTrackingRange(8).updateInterval(1)
            .build(PortMod.MOD_ID + ":venom"));
    public static final Supplier<SoundEvent> DILOPHOSAURUS_SPIT = Platform.register(Registry.SOUND_EVENT_REGISTRY, "entity.dilophosaurus.spit",
            () -> new SoundEvent(new ResourceLocation(PortMod.MOD_ID, "entity.dilophosaurus.spit")));
    private static final Map<Species, Supplier<EntityType<DinosaurEntity>>> TYPES = new EnumMap<>(Species.class);
    private static final Map<Species, Supplier<Item>> SPAWN_EGGS = new EnumMap<>(Species.class);
    private static final Map<Species, Supplier<Item>> RAW_MEATS = new EnumMap<>(Species.class);
    private static final Map<Species, Supplier<Item>> COOKED_MEATS = new EnumMap<>(Species.class);
    private static final Map<Species, Map<String, Supplier<SoundEvent>>> SOUND_EVENTS = new EnumMap<>(Species.class);
    public static final Supplier<Item> DINOSAUR_MEAT = Platform.register(Registry.ITEM_REGISTRY, "dinosaur_meat", () -> new Item(
            new Item.Properties().tab(JurassicContent.TAB).food(new FoodProperties.Builder().nutrition(3).saturationMod(.3f).meat().build())));

    static {
        for (Species species : Species.values()) {
            DinosaurProfile profile = DinosaurProfile.of(species);
            RAW_MEATS.put(species, Platform.register(Registry.ITEM_REGISTRY, species.id() + "_meat", () -> new GeneticsItem(
                    new Item.Properties().tab(JurassicContent.TAB).food(new FoodProperties.Builder().nutrition(3).saturationMod(.3f).meat().build()))));
            COOKED_MEATS.put(species, Platform.register(Registry.ITEM_REGISTRY, species.id() + "_steak", () -> new Item(
                    new Item.Properties().tab(JurassicContent.TAB).food(new FoodProperties.Builder().nutrition(8).saturationMod(.8f).meat().build()))));
            Supplier<EntityType<DinosaurEntity>> type = Platform.register(Registry.ENTITY_TYPE_REGISTRY, species.id(), () -> EntityType.Builder
                    .of(DinosaurEntity::new, species == Species.COELACANTH ? MobCategory.WATER_CREATURE : MobCategory.CREATURE)
                    .sized(profile.adultWidth(), profile.adultHeight()).clientTrackingRange(12)
                    .build(PortMod.MOD_ID + ":" + species.id()));
            TYPES.put(species, type);
            SPAWN_EGGS.put(species, Platform.register(Registry.ITEM_REGISTRY, species.id() + "_spawn_egg", () -> Platform.spawnEgg(type,
                    profile.eggPrimary(), profile.eggSecondary(), new Item.Properties().tab(JurassicContent.TAB), DinosaurSpawnEgg.INSTANCE)));
            if (species != Species.COELACANTH) {
                Map<String, Supplier<SoundEvent>> sounds = new java.util.HashMap<>();
                for (String kind : new String[] {"ambient", "hurt", "death"}) {
                    String id = "entity." + species.id() + "." + kind;
                    sounds.put(kind, Platform.register(Registry.SOUND_EVENT_REGISTRY, id, () -> new SoundEvent(new ResourceLocation(PortMod.MOD_ID, id))));
                }
                String[] extraSounds = switch (species) {
                    case TYRANNOSAURUS -> new String[] {"attack", "breathing", "call", "roar1", "roar2", "roar3", "drinking", "stomp"};
                    case VELOCIRAPTOR -> new String[] {"attack", "breathing", "call"};
                    case PARASAUROLOPHUS -> new String[] {"call"};
                    case MICRORAPTOR, MUSSAURUS -> new String[] {"attack"};
                    default -> new String[0];
                };
                for (String kind : extraSounds) {
                    String id = "entity." + species.id() + "." + kind;
                    sounds.put(kind, Platform.register(Registry.SOUND_EVENT_REGISTRY, id, () -> new SoundEvent(new ResourceLocation(PortMod.MOD_ID, id))));
                }
                SOUND_EVENTS.put(species, sounds);
            }
        }
    }

    private DinosaurRegistry() {}
    public static void init() {
        DinosaurGameRules.init();
        DinosaurNetwork.register();
    }
    public static void attributes(BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> sink) {
        for (Species species : Species.values()) {
            DinosaurProfile p = DinosaurProfile.of(species);
            sink.accept(type(species), DinosaurEntity.createMobAttributes()
                    .add(Attributes.MAX_HEALTH, p.adultHealth()).add(Attributes.MOVEMENT_SPEED, p.speed())
                    .add(Attributes.ATTACK_DAMAGE, p.adultDamage()).add(Attributes.FOLLOW_RANGE, 24).build());
        }
    }
    public static void spawnPlacements(JurassicEvents.SpawnPlacementSink sink) {
        for (Species species : Species.values()) {
            boolean aquatic = species == Species.COELACANTH;
            sink.register(type(species), aquatic ? SpawnPlacements.Type.IN_WATER : SpawnPlacements.Type.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, reason, pos, random) -> aquatic || level.getLevel().dimension().equals(Level.OVERWORLD));
        }
    }
    public static EntityType<DinosaurEntity> type(Species species) { return TYPES.get(species).get(); }
    public static Supplier<Item> spawnEgg(Species species) { return SPAWN_EGGS.get(species); }
    public static Supplier<Item> rawMeat(Species species) { return RAW_MEATS.get(species); }
    public static Supplier<Item> cookedMeat(Species species) { return COOKED_MEATS.get(species); }
    public static SoundEvent sound(Species species, String kind) {
        var sounds = SOUND_EVENTS.get(species);
        var sound = sounds == null ? null : sounds.get(kind);
        return sound == null ? null : sound.get();
    }
    /** Create and add a persistent animal. Returns null if it could not be added to the world. */
    public static DinosaurEntity spawn(Species species, ServerLevel level, BlockPos pos, boolean baby, boolean male) {
        return spawn(species, level, pos, baby, male, null);
    }
    /** Apply inherited size before testing whether a hatchling fits in the world. */
    public static DinosaurEntity spawn(Species species, ServerLevel level, BlockPos pos, boolean baby, boolean male,
            @javax.annotation.Nullable DinosaurAttributes inherited) {
        DinosaurEntity entity = type(species).create(level);
        if (entity == null) return null;
        entity.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, level.random.nextFloat() * 360, 0);
        entity.setBaby(baby);
        entity.setMale(male);
        if (inherited != null) entity.setGeneticAttributes(inherited);
        if (!level.noCollision(entity)) return null;
        entity.setPersistenceRequired();
        return level.addFreshEntity(entity) ? entity : null;
    }
}
