package net.mystcraft.integration.platform;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Loader-owned registration primitives; shared gameplay defines every entry once. */
public interface RegistryBridge {
    Supplier<net.minecraft.world.level.material.FlowingFluid> registerInkFluid(String name,boolean source);
    <P extends net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement> Supplier<net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType<P>> registerStructurePoolElementType(String name,Supplier<net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType<P>> factory);
    Item createLecternItem(Block block, Item.Properties properties);
    Block createInkFluidBlock();
    Item createInkBucket(Item.Properties properties);
    Supplier<com.mojang.serialization.Codec<? extends net.minecraft.world.level.biome.BiomeSource>> registerBiomeSourceCodec(String name, Supplier<com.mojang.serialization.Codec<? extends net.minecraft.world.level.biome.BiomeSource>> factory);
    Supplier<com.mojang.serialization.Codec<? extends net.minecraft.world.level.chunk.ChunkGenerator>> registerChunkGeneratorCodec(String name, Supplier<com.mojang.serialization.Codec<? extends net.minecraft.world.level.chunk.ChunkGenerator>> factory);
    Supplier<net.minecraft.world.entity.ai.village.poi.PoiType> registerPoi(String name, Supplier<net.minecraft.world.entity.ai.village.poi.PoiType> factory);
    Supplier<net.minecraft.world.entity.npc.VillagerProfession> registerProfession(String name, Supplier<net.minecraft.world.entity.npc.VillagerProfession> factory);
    <T extends net.minecraft.world.level.levelgen.feature.Feature<?>> Supplier<T> registerFeature(String name, Supplier<T> factory);
    Supplier<net.minecraft.world.level.storage.loot.functions.LootItemFunctionType> registerLootFunction(String name, Supplier<net.minecraft.world.level.storage.loot.functions.LootItemFunctionType> factory);
    <T extends Item> Supplier<T> registerItem(String name, Supplier<T> factory);
    <T extends Block> Supplier<T> registerBlock(String name, Supplier<T> factory);
    <T extends Entity> Supplier<EntityType<T>> registerEntityType(String name, Supplier<EntityType<T>> factory);
    <T extends BlockEntity> Supplier<BlockEntityType<T>> registerBlockEntityType(String name, Supplier<BlockEntityType<T>> factory);
    <T extends RecipeSerializer<?>> Supplier<T> registerRecipeSerializer(String name, Supplier<T> factory);
    Supplier<net.minecraft.core.particles.SimpleParticleType> registerSimpleParticle(String name);
    Supplier<SoundEvent> registerSoundEvent(String name, Supplier<SoundEvent> factory);
    <T extends AbstractContainerMenu> Supplier<MenuType<T>> registerMenu(String name, MenuFactory<T> factory);
    CreativeModeTab createCreativeTab(String name, Supplier<ItemStack> icon);

    @FunctionalInterface
    interface MenuFactory<T extends AbstractContainerMenu> {
        T create(int containerId, Inventory inventory, FriendlyByteBuf data);
    }
}
