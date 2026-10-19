package ports.jurassicraft.goat;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class GoatRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, PortMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, PortMod.MOD_ID);
    private static final DeferredRegister<Codec<? extends BiomeModifier>> BIOME_MODIFIERS = DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, PortMod.MOD_ID);
    public static final RegistryObject<EntityType<JurassicGoat>> GOAT = ENTITIES.register("goat", () -> EntityType.Builder
            .of(JurassicGoat::new, MobCategory.CREATURE).sized(.6F, 1.2F).clientTrackingRange(10).build(PortMod.MOD_ID + ":goat"));
    public static final RegistryObject<Item> EGG = ITEMS.register("goat_spawn_egg", () -> new ForgeSpawnEggItem(GOAT,
            0xEFEDE7, 0x7B3E20, new Item.Properties().tab(JurassicContent.TAB)));
    public static final RegistryObject<Item> RAW = ITEMS.register("goat_raw", () -> food(3, .3F));
    public static final RegistryObject<Item> COOKED = ITEMS.register("goat_cooked", () -> food(6, 1F));
    public static final RegistryObject<SoundEvent> AMBIENT = sound("goat_living");
    public static final RegistryObject<SoundEvent> HURT = sound("goat_hurt");
    public static final RegistryObject<SoundEvent> DEATH = sound("goat_death");
    public static final RegistryObject<Codec<? extends BiomeModifier>> SPAWN_MODIFIER = BIOME_MODIFIERS.register("goat_spawns", () -> Codec.unit(GoatBiomeModifier::new));
    private GoatRegistry() {}
    public static void register(IEventBus bus) {
        ENTITIES.register(bus); ITEMS.register(bus); SOUNDS.register(bus); BIOME_MODIFIERS.register(bus);
        bus.addListener(GoatRegistry::attributes);
        bus.addListener(GoatRegistry::spawnPlacement);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(GOAT.get(), Animal.createMobAttributes().add(Attributes.MAX_HEALTH, 7).add(Attributes.MOVEMENT_SPEED, .25).build());
    }
    private static void spawnPlacement(SpawnPlacementRegisterEvent event) {
        event.register(GOAT.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Animal::checkAnimalSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
    private static Item food(int nutrition, float saturation) {
        return new Item(new Item.Properties().tab(JurassicContent.TAB).food(new FoodProperties.Builder()
                .nutrition(nutrition).saturationMod(saturation).meat().build()));
    }
    private static RegistryObject<SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> new SoundEvent(new ResourceLocation(PortMod.MOD_ID, id)));
    }
}
