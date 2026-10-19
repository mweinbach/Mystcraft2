package ports.jurassicraft.goat;

import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.config.JurassicConfig;
import ports.jurassicraft.content.JurassicContent;

public final class GoatRegistry {
    public static final class Entry<T> implements Supplier<T> {
        private T value;
        private void set(T value) { this.value = Objects.requireNonNull(value); }
        @Override public T get() { return Objects.requireNonNull(value, "Goat registry not initialized"); }
    }
    public static final Entry<EntityType<JurassicGoat>> GOAT = new Entry<>();
    public static final Entry<Item> EGG = new Entry<>(), RAW = new Entry<>(), COOKED = new Entry<>();
    public static final Entry<SoundEvent> AMBIENT = new Entry<>(), HURT = new Entry<>(), DEATH = new Entry<>();
    private GoatRegistry() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID, path); }
    private static void sound(Entry<SoundEvent> entry, String name) {
        entry.set(Registry.register(Registry.SOUND_EVENT, id(name), new SoundEvent(id(name))));
    }
    private static Item food(int nutrition, float saturation) {
        return new Item(new Item.Properties().tab(JurassicContent.TAB).food(new FoodProperties.Builder()
                .nutrition(nutrition).saturationMod(saturation).meat().build()));
    }
    static boolean allowsNaturalSpawn(Biome biome, boolean enabled) {
        return enabled && biome.getMobSettings().getMobs(MobCategory.CREATURE).unwrap().stream()
                .anyMatch(spawn -> spawn.type == EntityType.PIG);
    }
    public static void init() {
        GOAT.set(Registry.register(Registry.ENTITY_TYPE, id("goat"), FabricEntityTypeBuilder.<JurassicGoat>createMob()
                .spawnGroup(MobCategory.CREATURE).entityFactory(JurassicGoat::new)
                .dimensions(EntityDimensions.scalable(.6F, 1.2F)).trackRangeChunks(10)
                .defaultAttributes(() -> Animal.createMobAttributes().add(Attributes.MAX_HEALTH, 7)
                        .add(Attributes.MOVEMENT_SPEED, .25))
                .spawnRestriction(SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        Animal::checkAnimalSpawnRules).build()));
        EGG.set(Registry.register(Registry.ITEM, id("goat_spawn_egg"),
                new SpawnEggItem(GOAT.get(), 0xEFEDE7, 0x7B3E20, new Item.Properties().tab(JurassicContent.TAB))));
        RAW.set(Registry.register(Registry.ITEM, id("goat_raw"), food(3, .3F)));
        COOKED.set(Registry.register(Registry.ITEM, id("goat_cooked"), food(6, 1F)));
        sound(AMBIENT, "goat_living"); sound(HURT, "goat_hurt"); sound(DEATH, "goat_death");
        if (JurassicConfig.NATURAL_GOAT_SPAWNING.get()) {
            // As upstream, use the actual biome spawn list so datapack and modded pig biomes work too.
            BiomeModifications.addSpawn(context -> allowsNaturalSpawn(context.getBiome(), true),
                    MobCategory.CREATURE, GOAT.get(), 10, 2, 4);
        }
    }
}
