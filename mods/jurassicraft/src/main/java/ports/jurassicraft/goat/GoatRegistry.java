package ports.jurassicraft.goat;

import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import ports.jurassicraft.JurassicEvents;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.platform.Platform;

public final class GoatRegistry {
    public static final Supplier<EntityType<JurassicGoat>> GOAT = Platform.register(Registry.ENTITY_TYPE_REGISTRY, "goat", () -> EntityType.Builder
            .of(JurassicGoat::new, MobCategory.CREATURE).sized(.6F, 1.2F).clientTrackingRange(10).build(PortMod.MOD_ID + ":goat"));
    public static final Supplier<Item> EGG = Platform.register(Registry.ITEM_REGISTRY, "goat_spawn_egg", () -> Platform.spawnEgg(GOAT,
            0xEFEDE7, 0x7B3E20, new Item.Properties().tab(JurassicContent.TAB)));
    public static final Supplier<Item> RAW = Platform.register(Registry.ITEM_REGISTRY, "goat_raw", () -> food(3, .3F));
    public static final Supplier<Item> COOKED = Platform.register(Registry.ITEM_REGISTRY, "goat_cooked", () -> food(6, 1F));
    public static final Supplier<SoundEvent> AMBIENT = sound("goat_living");
    public static final Supplier<SoundEvent> HURT = sound("goat_hurt");
    public static final Supplier<SoundEvent> DEATH = sound("goat_death");
    private GoatRegistry() {}
    public static void init() {}
    public static void attributes(BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> sink) {
        sink.accept(GOAT.get(), Animal.createMobAttributes().add(Attributes.MAX_HEALTH, 7).add(Attributes.MOVEMENT_SPEED, .25).build());
    }
    public static void spawnPlacements(JurassicEvents.SpawnPlacementSink sink) {
        sink.register(GOAT.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
    }
    private static Item food(int nutrition, float saturation) {
        return new Item(new Item.Properties().tab(JurassicContent.TAB).food(new FoodProperties.Builder()
                .nutrition(nutrition).saturationMod(saturation).meat().build()));
    }
    private static Supplier<SoundEvent> sound(String id) {
        return Platform.register(Registry.SOUND_EVENT_REGISTRY, id, () -> new SoundEvent(new ResourceLocation(PortMod.MOD_ID, id)));
    }
}
