package net.mystcraft.integration.platform;

import java.util.function.Supplier;
import net.mystcraft.integration.MystcraftBootstrap;
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
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Forge defers construction until its registry events fire. */
public final class ForgeRegistryBridge implements RegistryBridge {
    private final DeferredRegister<net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType<?>> poolElements=DeferredRegister.create(net.minecraft.core.Registry.STRUCTURE_POOL_ELEMENT_REGISTRY,MystcraftBootstrap.MOD_ID);
    @Override public <P extends net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement> Supplier<net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType<P>> registerStructurePoolElementType(String name,Supplier<net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType<P>> factory){return poolElements.register(name,factory);}
    private final DeferredRegister<net.minecraft.world.level.material.Fluid> fluids=DeferredRegister.create(ForgeRegistries.FLUIDS,MystcraftBootstrap.MOD_ID);
    private final DeferredRegister<net.minecraftforge.fluids.FluidType> fluidTypes=DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES,MystcraftBootstrap.MOD_ID);
    private Supplier<net.minecraftforge.fluids.FluidType> inkType;
    @Override public Supplier<net.minecraft.world.level.material.FlowingFluid> registerInkFluid(String name,boolean source){
        if(inkType==null)inkType=fluidTypes.register("black_ink",net.mystcraft.forge.fluid.ForgeInkFluidType::new);
        return fluids.register(name,()->new net.mystcraft.forge.fluid.ForgeInkFluid(source,inkType));
    }
    @Override public Item createInkBucket(Item.Properties properties){return new net.minecraft.world.item.BucketItem(net.mystcraft.integration.registry.InkRegistry.BLACK_INK,properties){
        @Override public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(ItemStack stack,net.minecraft.nbt.CompoundTag tag){return new net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper(stack);}
        @Override public boolean emptyContents(net.minecraft.world.entity.player.Player player,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.world.phys.BlockHitResult hit){return level.dimensionType().ultraWarm()?net.mystcraft.minecraft.fluid.InkPlacement.inWarmDimension(player,level,pos,hit):super.emptyContents(player,level,pos,hit);}
        @Override public boolean emptyContents(net.minecraft.world.entity.player.Player player,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.world.phys.BlockHitResult hit,ItemStack container){return level.dimensionType().ultraWarm()?net.mystcraft.minecraft.fluid.InkPlacement.inWarmDimension(player,level,pos,hit):super.emptyContents(player,level,pos,hit,container);}
    };}
    @Override public Item createLecternItem(Block block, Item.Properties properties) { return new net.mystcraft.forge.exploration.ForgeLecternItem(block, properties); }
    @Override public Block createInkFluidBlock(){return new net.minecraft.world.level.block.LiquidBlock(net.mystcraft.integration.registry.InkRegistry.BLACK_INK,Block.Properties.of(net.minecraft.world.level.material.Material.WATER).noCollission().strength(100F).noLootTable()){
        @Override public int getLightBlock(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos pos){return 3;}
    };}
    private final DeferredRegister<Item> items = DeferredRegister.create(ForgeRegistries.ITEMS, MystcraftBootstrap.MOD_ID);
    private final DeferredRegister<Block> blocks = DeferredRegister.create(ForgeRegistries.BLOCKS, MystcraftBootstrap.MOD_ID);
    private final DeferredRegister<EntityType<?>> entities = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MystcraftBootstrap.MOD_ID);
    private final DeferredRegister<BlockEntityType<?>> blockEntities = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MystcraftBootstrap.MOD_ID);
    private final DeferredRegister<RecipeSerializer<?>> recipes = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MystcraftBootstrap.MOD_ID);
    private final DeferredRegister<SoundEvent> sounds = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MystcraftBootstrap.MOD_ID);
    private final DeferredRegister<MenuType<?>> menus = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MystcraftBootstrap.MOD_ID);

    private final DeferredRegister<net.minecraft.world.level.levelgen.feature.Feature<?>> features = DeferredRegister.create(ForgeRegistries.FEATURES, MystcraftBootstrap.MOD_ID);
    private final DeferredRegister<net.minecraft.world.level.storage.loot.functions.LootItemFunctionType> lootFunctions = DeferredRegister.create(net.minecraft.core.Registry.LOOT_FUNCTION_REGISTRY, MystcraftBootstrap.MOD_ID);

    private final DeferredRegister<net.minecraft.world.entity.ai.village.poi.PoiType> pois = DeferredRegister.create(ForgeRegistries.POI_TYPES, MystcraftBootstrap.MOD_ID);
    private final DeferredRegister<net.minecraft.world.entity.npc.VillagerProfession> professions = DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, MystcraftBootstrap.MOD_ID);
    @Override public Supplier<net.minecraft.world.entity.ai.village.poi.PoiType> registerPoi(String name, Supplier<net.minecraft.world.entity.ai.village.poi.PoiType> factory) { return pois.register(name, factory); }
    @Override public Supplier<net.minecraft.world.entity.npc.VillagerProfession> registerProfession(String name, Supplier<net.minecraft.world.entity.npc.VillagerProfession> factory) { return professions.register(name, factory); }
    private final DeferredRegister<com.mojang.serialization.Codec<? extends net.minecraft.world.level.biome.BiomeSource>> biomeSources = DeferredRegister.create(net.minecraft.core.Registry.BIOME_SOURCE_REGISTRY, MystcraftBootstrap.MOD_ID);
    @Override public Supplier<com.mojang.serialization.Codec<? extends net.minecraft.world.level.biome.BiomeSource>> registerBiomeSourceCodec(String name, Supplier<com.mojang.serialization.Codec<? extends net.minecraft.world.level.biome.BiomeSource>> factory) { return biomeSources.register(name, factory); }
    private final DeferredRegister<com.mojang.serialization.Codec<? extends net.minecraft.world.level.chunk.ChunkGenerator>> generators = DeferredRegister.create(net.minecraft.core.Registry.CHUNK_GENERATOR_REGISTRY, MystcraftBootstrap.MOD_ID);
    @Override public Supplier<com.mojang.serialization.Codec<? extends net.minecraft.world.level.chunk.ChunkGenerator>> registerChunkGeneratorCodec(String name, Supplier<com.mojang.serialization.Codec<? extends net.minecraft.world.level.chunk.ChunkGenerator>> factory) { return generators.register(name, factory); }
    private final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> particles=DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES,MystcraftBootstrap.MOD_ID);
    @Override public Supplier<net.minecraft.core.particles.SimpleParticleType> registerSimpleParticle(String name){return particles.register(name,()->new net.minecraft.core.particles.SimpleParticleType(false){});}
    public void attach(IEventBus bus) {
        particles.register(bus);
        poolElements.register(bus);fluids.register(bus);fluidTypes.register(bus);
        generators.register(bus);
        biomeSources.register(bus);
        pois.register(bus);
        professions.register(bus);
        features.register(bus);
        lootFunctions.register(bus);
        blocks.register(bus);
        items.register(bus);
        entities.register(bus);
        blockEntities.register(bus);
        recipes.register(bus);
        sounds.register(bus);
        menus.register(bus);
    }
    @Override public <T extends net.minecraft.world.level.levelgen.feature.Feature<?>> Supplier<T> registerFeature(String name, Supplier<T> factory) { return features.register(name, factory); }
    @Override public Supplier<net.minecraft.world.level.storage.loot.functions.LootItemFunctionType> registerLootFunction(String name, Supplier<net.minecraft.world.level.storage.loot.functions.LootItemFunctionType> factory) { return lootFunctions.register(name, factory); }
    @Override public <T extends Item> Supplier<T> registerItem(String name, Supplier<T> factory) { return items.register(name, factory); }
    @Override public <T extends Block> Supplier<T> registerBlock(String name, Supplier<T> factory) { return blocks.register(name, factory); }
    @Override public <T extends Entity> Supplier<EntityType<T>> registerEntityType(String name, Supplier<EntityType<T>> factory) { return entities.register(name, factory); }
    @Override public <T extends BlockEntity> Supplier<BlockEntityType<T>> registerBlockEntityType(String name, Supplier<BlockEntityType<T>> factory) { return blockEntities.register(name, factory); }
    @Override public <T extends RecipeSerializer<?>> Supplier<T> registerRecipeSerializer(String name, Supplier<T> factory) { return recipes.register(name, factory); }
    @Override public Supplier<SoundEvent> registerSoundEvent(String name, Supplier<SoundEvent> factory) { return sounds.register(name, factory); }
    @Override public <T extends AbstractContainerMenu> Supplier<MenuType<T>> registerMenu(String name, MenuFactory<T> factory) { return menus.register(name, () -> IForgeMenuType.create(factory::create)); }
    @Override public CreativeModeTab createCreativeTab(String name, Supplier<ItemStack> icon) {
        return new CreativeModeTab(name) {
            @Override public ItemStack makeIcon() { return icon.get(); }
        };
    }
}
