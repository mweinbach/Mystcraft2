package ports.jurassicraft.entity;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.genetics.DinosaurAttributes;
import ports.jurassicraft.genetics.GeneticsItem;

/** Fabric registrations for the ten species and their eggs, sounds, and meat. */
public final class DinosaurRegistry {
    private static final Map<Species, EntityType<DinosaurEntity>> TYPES = new EnumMap<>(Species.class);
    private static final Map<Species, Item> SPAWN_EGGS = new EnumMap<>(Species.class);
    private static final Map<Species, Item> RAW_MEATS = new EnumMap<>(Species.class);
    private static final Map<Species, Item> COOKED_MEATS = new EnumMap<>(Species.class);
    private static final Map<Species, Map<String, SoundEvent>> SOUNDS = new EnumMap<>(Species.class);
    private static EntityType<DinosaurEggEntity> egg;
    private static EntityType<VenomEntity> venom;
    private static SoundEvent spit;
    private static Item meat;
    private static MenuType<DinosaurOrderMenu> orderMenu;
    // Suppliers retain the common port's existing registry access API.
    public static final Supplier<EntityType<DinosaurEggEntity>> EGG = () -> egg;
    public static final Supplier<EntityType<VenomEntity>> VENOM = () -> venom;
    public static final Supplier<SoundEvent> DILOPHOSAURUS_SPIT = () -> spit;
    public static final Supplier<Item> DINOSAUR_MEAT = () -> meat;
    public static final Supplier<MenuType<DinosaurOrderMenu>> ORDER_MENU = () -> orderMenu;

    private DinosaurRegistry() {}

    public static void init() {
        DinosaurGameRules.init();
        DinosaurNetwork.register();
        egg = Registry.register(Registry.ENTITY_TYPE, id("dinosaur_egg"),
                FabricEntityTypeBuilder.<DinosaurEggEntity>create(MobCategory.MISC, DinosaurEggEntity::new)
                        .dimensions(EntityDimensions.scalable(.3f, .5f)).trackRangeChunks(8).trackedUpdateRate(3).build());
        venom = Registry.register(Registry.ENTITY_TYPE, id("venom"),
                FabricEntityTypeBuilder.<VenomEntity>create(MobCategory.MISC, VenomEntity::new)
                        .dimensions(EntityDimensions.scalable(.25f, .25f)).trackRangeChunks(8).trackedUpdateRate(1).build());
        spit = registerSound("entity.dilophosaurus.spit");
        orderMenu = ScreenHandlerRegistry.registerExtended(id("dinosaur_order"), DinosaurOrderMenu::new);
        meat = Registry.register(Registry.ITEM, id("dinosaur_meat"), new Item(new Item.Properties().tab(JurassicContent.TAB)
                .food(new FoodProperties.Builder().nutrition(3).saturationMod(.3f).meat().build())));
        for (Species species : Species.values()) {
            DinosaurProfile profile = DinosaurProfile.of(species);
            boolean aquatic = species == Species.COELACANTH;
            EntityType<DinosaurEntity> type = FabricEntityTypeBuilder.<DinosaurEntity>createMob()
                    .spawnGroup(aquatic ? MobCategory.WATER_CREATURE : MobCategory.CREATURE)
                    .entityFactory(DinosaurEntity::new)
                    .dimensions(EntityDimensions.scalable(profile.adultWidth(), profile.adultHeight()))
                    .trackRangeChunks(12)
                    .defaultAttributes(() -> DinosaurEntity.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, profile.adultHealth()).add(Attributes.MOVEMENT_SPEED, profile.speed())
                            .add(Attributes.ATTACK_DAMAGE, profile.adultDamage()).add(Attributes.FOLLOW_RANGE, 24))
                    .spawnRestriction(aquatic ? SpawnPlacements.Type.IN_WATER : SpawnPlacements.Type.ON_GROUND,
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            (entityType, level, reason, pos, random) -> aquatic || level.getLevel().dimension().equals(Level.OVERWORLD))
                    .build();
            TYPES.put(species, Registry.register(Registry.ENTITY_TYPE, id(species.id()), type));
            SPAWN_EGGS.put(species, Registry.register(Registry.ITEM, id(species.id() + "_spawn_egg"),
                    new DinosaurSpawnEggItem(type, profile.eggPrimary(), profile.eggSecondary(), new Item.Properties().tab(JurassicContent.TAB))));
            RAW_MEATS.put(species, Registry.register(Registry.ITEM, id(species.id() + "_meat"), new GeneticsItem(
                    new Item.Properties().tab(JurassicContent.TAB).food(new FoodProperties.Builder().nutrition(3).saturationMod(.3f).meat().build()))));
            COOKED_MEATS.put(species, Registry.register(Registry.ITEM, id(species.id() + "_steak"), new Item(
                    new Item.Properties().tab(JurassicContent.TAB).food(new FoodProperties.Builder().nutrition(8).saturationMod(.8f).meat().build()))));
            if (aquatic) continue;
            Map<String, SoundEvent> sounds = new HashMap<>();
            for (String kind : new String[] {"ambient", "hurt", "death"})
                sounds.put(kind, registerSound("entity." + species.id() + "." + kind));
            String[] extra = switch (species) {
                case TYRANNOSAURUS -> new String[] {"attack", "breathing", "call", "roar1", "roar2", "roar3", "drinking", "stomp"};
                case VELOCIRAPTOR -> new String[] {"attack", "breathing", "call"};
                case PARASAUROLOPHUS -> new String[] {"call"};
                case MICRORAPTOR, MUSSAURUS -> new String[] {"attack"};
                default -> new String[0];
            };
            for (String kind : extra) sounds.put(kind, registerSound("entity." + species.id() + "." + kind));
            SOUNDS.put(species, sounds);
        }
        DinosaurBiomeModifier.init();
        AnimateCommand.init();
    }
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID, path); }
    private static SoundEvent registerSound(String name) {
        ResourceLocation id = id(name);
        return Registry.register(Registry.SOUND_EVENT, id, new SoundEvent(id));
    }
    public static EntityType<DinosaurEntity> type(Species species) { return TYPES.get(species); }
    public static Supplier<Item> spawnEgg(Species species) { return () -> SPAWN_EGGS.get(species); }
    public static Supplier<Item> rawMeat(Species species) { return () -> RAW_MEATS.get(species); }
    public static Supplier<Item> cookedMeat(Species species) { return () -> COOKED_MEATS.get(species); }
    @Nullable public static SoundEvent sound(Species species, String kind) {
        Map<String, SoundEvent> sounds = SOUNDS.get(species);
        return sounds == null ? null : sounds.get(kind);
    }
    public static DinosaurEntity spawn(Species species, ServerLevel level, BlockPos pos, boolean baby, boolean male) {
        return spawn(species, level, pos, baby, male, null);
    }
    @Nullable public static DinosaurEntity spawn(Species species, ServerLevel level, BlockPos pos, boolean baby, boolean male,
            @Nullable DinosaurAttributes inherited) {
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
