package net.mystcraft.fabric;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.mystcraft.integration.platform.RegistryBridge;

/** Registers the content declared by the common bootstrap into Fabric's registries. */
public final class FabricRegistryBridge implements RegistryBridge {
    @Override public Supplier<net.minecraft.core.particles.SimpleParticleType> registerSimpleParticle(String name){var value=Registry.register(Registry.PARTICLE_TYPE,id(name),net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple());return ()->value;}
    @Override public <P extends net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement> Supplier<net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType<P>> registerStructurePoolElementType(String name,Supplier<net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType<P>> factory){var element=Registry.register(Registry.STRUCTURE_POOL_ELEMENT,id(name),factory.get());return ()->element;}
    @Override public Supplier<net.minecraft.world.level.material.FlowingFluid> registerInkFluid(String name,boolean source){var fluid=Registry.register(Registry.FLUID,id(name),new net.mystcraft.minecraft.fluid.ClassicInkFluid(source));return ()->fluid;}
    @Override public Item createInkBucket(Item.Properties properties){return new net.minecraft.world.item.BucketItem(net.mystcraft.integration.registry.InkRegistry.BLACK_INK.get(),properties){
        @Override public boolean emptyContents(net.minecraft.world.entity.player.Player player,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.world.phys.BlockHitResult hit){return level.dimensionType().ultraWarm()?net.mystcraft.minecraft.fluid.InkPlacement.inWarmDimension(player,level,pos,hit):super.emptyContents(player,level,pos,hit);}
    };}
    @Override public Item createLecternItem(Block block, Item.Properties properties) { return new net.minecraft.world.item.BlockItem(block, properties); }
    @Override public Block createInkFluidBlock(){return new net.minecraft.world.level.block.LiquidBlock(net.mystcraft.integration.registry.InkRegistry.BLACK_INK.get(),Block.Properties.of(net.minecraft.world.level.material.Material.WATER).noCollission().strength(100F).noLootTable()){
        @Override public int getLightBlock(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos pos){return 3;}
    };}
    @Override public Supplier<net.minecraft.world.entity.ai.village.poi.PoiType> registerPoi(String name, Supplier<net.minecraft.world.entity.ai.village.poi.PoiType> factory) {
        var definition = factory.get();
        var value = net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper.register(
                id(name), definition.maxTickets(), definition.validRange(), (Iterable<net.minecraft.world.level.block.state.BlockState>) definition.matchingStates());
        return () -> value;
    }
    @Override public Supplier<net.minecraft.world.entity.npc.VillagerProfession> registerProfession(String name, Supplier<net.minecraft.world.entity.npc.VillagerProfession> factory) {
        var value = Registry.register(Registry.VILLAGER_PROFESSION, id(name), factory.get()); return () -> value;
    }
    @Override public Supplier<com.mojang.serialization.Codec<? extends net.minecraft.world.level.biome.BiomeSource>> registerBiomeSourceCodec(String name, Supplier<com.mojang.serialization.Codec<? extends net.minecraft.world.level.biome.BiomeSource>> factory) {
        var value = Registry.register(Registry.BIOME_SOURCE, id(name), factory.get()); return () -> value;
    }
    @Override public Supplier<com.mojang.serialization.Codec<? extends net.minecraft.world.level.chunk.ChunkGenerator>> registerChunkGeneratorCodec(String name, Supplier<com.mojang.serialization.Codec<? extends net.minecraft.world.level.chunk.ChunkGenerator>> factory) {
        var value = Registry.register(Registry.CHUNK_GENERATOR, id(name), factory.get()); return () -> value;
    }
    private static ResourceLocation id(String name) { return new ResourceLocation("mystcraft", name); }
    @Override public <T extends net.minecraft.world.level.levelgen.feature.Feature<?>> Supplier<T> registerFeature(String name, Supplier<T> factory) {
        T value = Registry.register(Registry.FEATURE, id(name), factory.get()); return () -> value;
    }
    @Override public Supplier<net.minecraft.world.level.storage.loot.functions.LootItemFunctionType> registerLootFunction(String name, Supplier<net.minecraft.world.level.storage.loot.functions.LootItemFunctionType> factory) {
        var value = Registry.register(Registry.LOOT_FUNCTION_TYPE, id(name), factory.get()); return () -> value;
    }
    @Override public <T extends Item> Supplier<T> registerItem(String name, Supplier<T> factory) {
        T value = Registry.register(Registry.ITEM, id(name), factory.get());
        return () -> value;
    }
    @Override public <T extends Block> Supplier<T> registerBlock(String name, Supplier<T> factory) {
        T value = Registry.register(Registry.BLOCK, id(name), factory.get());
        return () -> value;
    }
    @Override public <T extends Entity> Supplier<EntityType<T>> registerEntityType(String name, Supplier<EntityType<T>> factory) {
        EntityType<T> value = Registry.register(Registry.ENTITY_TYPE, id(name), factory.get());
        return () -> value;
    }
    @Override public <T extends BlockEntity> Supplier<BlockEntityType<T>> registerBlockEntityType(String name, Supplier<BlockEntityType<T>> factory) {
        BlockEntityType<T> value = Registry.register(Registry.BLOCK_ENTITY_TYPE, id(name), factory.get());
        return () -> value;
    }
    @Override public <T extends RecipeSerializer<?>> Supplier<T> registerRecipeSerializer(String name, Supplier<T> factory) {
        T value = Registry.register(Registry.RECIPE_SERIALIZER, id(name), factory.get());
        return () -> value;
    }
    @Override public Supplier<SoundEvent> registerSoundEvent(String name, Supplier<SoundEvent> factory) {
        SoundEvent value = Registry.register(Registry.SOUND_EVENT, id(name), factory.get());
        return () -> value;
    }
    @Override public <T extends AbstractContainerMenu> Supplier<MenuType<T>> registerMenu(String name, MenuFactory<T> factory) {
        MenuType<T> value = Registry.register(Registry.MENU, id(name), new ExtendedScreenHandlerType<>(factory::create));
        return () -> value;
    }
    @Override public CreativeModeTab createCreativeTab(String name, Supplier<ItemStack> icon) {
        return FabricItemGroupBuilder.create(id(name)).icon(icon).build();
    }
}
