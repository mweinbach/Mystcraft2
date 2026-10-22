/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.registries;

import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.network.syncher.EntityDataSerializer;
import ports.mekanism.lib.common.world.BiomeModifier;
import ports.mekanism.lib.common.world.StructureModifier;
import ports.mekanism.lib.fluids.FluidType;
import com.mojang.serialization.Codec;

/**
 * Views of the vanilla registries Mekanism reads, and the keys of Forge's own registries. On Fabric the backend
 * supplies plain in-memory registries for the Forge-only keys.
 */
public final class ForgeRegistries {

    public static final IForgeRegistry<Block> BLOCKS = vanilla(Registry.BLOCK);
    public static final IForgeRegistry<Fluid> FLUIDS = vanilla(Registry.FLUID);
    public static final IForgeRegistry<Item> ITEMS = vanilla(Registry.ITEM);
    public static final IForgeRegistry<MobEffect> MOB_EFFECTS = vanilla(Registry.MOB_EFFECT);
    public static final IForgeRegistry<SoundEvent> SOUND_EVENTS = vanilla(Registry.SOUND_EVENT);
    public static final IForgeRegistry<Potion> POTIONS = vanilla(Registry.POTION);
    public static final IForgeRegistry<Enchantment> ENCHANTMENTS = vanilla(Registry.ENCHANTMENT);
    public static final IForgeRegistry<EntityType<?>> ENTITY_TYPES = vanilla(Registry.ENTITY_TYPE);
    public static final IForgeRegistry<BlockEntityType<?>> BLOCK_ENTITY_TYPES = vanilla(Registry.BLOCK_ENTITY_TYPE);
    public static final IForgeRegistry<ParticleType<?>> PARTICLE_TYPES = vanilla(Registry.PARTICLE_TYPE);
    public static final IForgeRegistry<MenuType<?>> MENU_TYPES = vanilla(Registry.MENU);
    public static final IForgeRegistry<RecipeType<?>> RECIPE_TYPES = vanilla(Registry.RECIPE_TYPE);
    public static final IForgeRegistry<RecipeSerializer<?>> RECIPE_SERIALIZERS = vanilla(Registry.RECIPE_SERIALIZER);
    public static final IForgeRegistry<Attribute> ATTRIBUTES = vanilla(Registry.ATTRIBUTE);
    public static final IForgeRegistry<Feature<?>> FEATURES = vanilla(Registry.FEATURE);
    public static final IForgeRegistry<Biome> BIOMES = vanilla(BuiltinRegistries.BIOME);

    private ForgeRegistries() {
    }

    @SuppressWarnings("unchecked")
    private static <T> IForgeRegistry<T> vanilla(Registry<? extends T> registry) {
        IForgeRegistry<T> view = new VanillaForgeRegistry<>(registry);
        RegistryManager.ACTIVE.add(view);
        return view;
    }

    public static final class Keys {

        public static final ResourceKey<Registry<Block>> BLOCKS = Registry.BLOCK_REGISTRY;
        public static final ResourceKey<Registry<Fluid>> FLUIDS = Registry.FLUID_REGISTRY;
        public static final ResourceKey<Registry<Item>> ITEMS = Registry.ITEM_REGISTRY;
        public static final ResourceKey<Registry<RecipeSerializer<?>>> RECIPE_SERIALIZERS = Registry.RECIPE_SERIALIZER_REGISTRY;
        public static final ResourceKey<Registry<FluidType>> FLUID_TYPES = forge("fluid_type");
        public static final ResourceKey<Registry<EntityDataSerializer<?>>> ENTITY_DATA_SERIALIZERS = forge("entity_data_serializers");
        public static final ResourceKey<Registry<Codec<? extends BiomeModifier>>> BIOME_MODIFIER_SERIALIZERS = forge("biome_modifier_serializers");
        public static final ResourceKey<Registry<Codec<? extends StructureModifier>>> STRUCTURE_MODIFIER_SERIALIZERS = forge("structure_modifier_serializers");

        private Keys() {
        }

        private static <T> ResourceKey<Registry<T>> forge(String name) {
            return ResourceKey.createRegistryKey(new ResourceLocation("forge", name));
        }
    }
}
