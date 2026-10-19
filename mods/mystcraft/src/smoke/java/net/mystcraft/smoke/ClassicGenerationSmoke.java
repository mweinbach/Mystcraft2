package net.mystcraft.smoke;

import net.mystcraft.minecraft.age.generation.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Test-only real registry/state/chunk checks; writes no canonical Age or live world blocks. */
public final class ClassicGenerationSmoke {
    public static List<String> run(MinecraftServer server,ServerLevel level){
        check(server.isSameThread(),"generation smoke uses server thread");var passed=new ArrayList<String>();
        var biomes=server.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);var structures=server.registryAccess().registryOrThrow(Registry.STRUCTURE_SET_REGISTRY);
        var result=ClassicGenerationCompiler.compileExecution(List.of(page("biome/minecraft/plains"),page("biome/minecraft/desert"),page("biocongrid"),page("modmat_stone_0"),page("modmat_water_0"),page("terrainflat")),12345L);
        check(result.errors().isEmpty(),"valid classic queued parameters compile");check(result.plan().biomes().equals(List.of(new ResourceLocation("minecraft:desert"),new ResourceLocation("minecraft:plains"))),"biome queue consumes last first");
        check(result.plan().fill().is(Blocks.STONE)&&result.plan().sea().is(Blocks.WATER),"sea then terrain newest matching material");
        var cleared=ClassicGenerationCompiler.compileExecution(List.of(page("modmat_stone_0"),page("modclear"),page("biome/minecraft/plains"),page("terrainvoid")),0);
        check(cleared.errors().isEmpty()&&cleared.danglingInstability()==110,"clear costs20percent then dangling biome100");
        var duplicate=ClassicGenerationCompiler.compileExecution(List.of(page("terrainflat"),page("terrainvoid"),page("biocongrid"),page("biocontiled")),0);
        check(duplicate.errors().isEmpty()&&duplicate.plan().terrain().terrain().equals("void")&&duplicate.plan().biomeController().equals("tiled")&&duplicate.controllerDuplicates()==2,"last controller wins with separately counted cost");
        var retainedLevel=ClassicGenerationCompiler.compileExecution(List.of(page("terrainnether"),page("terrainflat")),0);
        check(retainedLevel.plan().terrain().seaLevel()==32,"later flat controller preserves earlier Nether sea-level mutation");
        var averageLevel=ClassicGenerationCompiler.compileExecution(List.of(page("terrainnether"),page("terrainend"),page("terrainflat")),0);
        check(averageLevel.plan().terrain().seaLevel()==40,"source nullable sea setters average32and49withintegertruncation");
        passed.add("Generation: source biome LIFO, category material matching, clear20percent/dangling costs and duplicate last-controller semantics");
        check(result.plan().terrain().version()==4,"new execution freezes specialized surface version4");
        var resolved=result.plan().resolveBiomes(biomes,12345L);var restored=ClassicGenerationPlan.fromTag(resolved.toTag());check(restored.toTag().equals(resolved.toTag()),"all frozen generation inputs roundtrip");
        var source=(ClassicBiomeSource)restored.biomeSource(biomes,12345L);
        check(source.indexAtBlock(-1,0)==1&&source.indexAtBlock(16,0)==1,"grid floor-mod at negative and positive boundaries");
        var tiled=new ClassicBiomeSource(List.of(biomes.getHolderOrThrow(Biomes.DESERT),biomes.getHolderOrThrow(Biomes.PLAINS)),"tiled",12345L);
        check(tiled.densityBiome(4,0).is(Biomes.DESERT)&&((net.minecraft.world.level.biome.BiomeSource) tiled).getNoiseBiome(4,0,0,net.minecraft.world.level.biome.Climate.empty()).is(Biomes.PLAINS),"tiled source preserves distinct density coordinate scale");
        CompoundTag statePage=page("material"),state=new CompoundTag(),properties=new CompoundTag();state.putString("Name","minecraft:oak_log");properties.putString("axis","x");state.put("Properties",properties);statePage.put("BlockState",state);statePage.putBoolean("Terrain",true);
        var oriented=ClassicGenerationCompiler.compileExecution(List.of(statePage,page("terrainflat")),0).plan();check(oriented.fill().getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)==net.minecraft.core.Direction.Axis.X,"material block-state properties persist");
        var generator=new ClassicChunkGenerator(structures,source,12345L,restored.terrain(),restored);check(generator.column(0,0)[10].is(Blocks.STONE),"new frozen flat terrain executable");
        check(!net.mystcraft.minecraft.exploration.ClassicCrystalFeature.usesCompatibilityPopulation(generator),"v4 no-crystal-page plan must suppress globally injected crystal population");
        var authoredCrystals=ClassicGenerationCompiler.compileExecution(List.of(page("cryform"),page("terrainflat")),0).plan().resolveBiomes(biomes,12345L);
        check(authoredCrystals.alterations().stream().filter(a->a.kind().equals("cryform")).count()==1,"authored crystal page retains exactly one ordered population effect");
        var authoredGenerator=new ClassicChunkGenerator(structures,authoredCrystals.biomeSource(biomes,12345L),12345L,authoredCrystals.terrain(),authoredCrystals);
        check(!net.mystcraft.minecraft.exploration.ClassicCrystalFeature.usesCompatibilityPopulation(authoredGenerator),"authored v4 crystals must not also receive compatibility population");
        var legacyV1=new ClassicTerrainSpec(1,"flat",new net.minecraft.resources.ResourceLocation("minecraft:stone"),new net.minecraft.resources.ResourceLocation("minecraft:water"),64,63,true);
        var legacyV2=new ClassicTerrainSpec(2,"normal",new net.minecraft.resources.ResourceLocation("minecraft:stone"),new net.minecraft.resources.ResourceLocation("minecraft:water"),64,63,true);
        check(net.mystcraft.minecraft.exploration.ClassicCrystalFeature.usesCompatibilityPopulation(new ClassicChunkGenerator(structures,source,12345L,legacyV1))&&net.mystcraft.minecraft.exploration.ClassicCrystalFeature.usesCompatibilityPopulation(new ClassicChunkGenerator(structures,source,12345L,legacyV2)),"legacy v1/v2 retain existing compatibility crystal population");
        check(net.mystcraft.minecraft.exploration.ClassicCrystalFeature.usesCompatibilityPopulation(server.overworld().getChunkSource().getGenerator()),"ordinary legacy/native generator retains compatibility feature dimension gating");
        passed.add("Generation: no-page and authored v4 plans suppress duplicate global crystals; legacy v1/v2/native compatibility behavior remains");
        passed.add("Generation: frozen registry palettes and oriented block-state NBT roundtrip; grid negatives and tiled density scale match source");
        passed.addAll(SkylandsPendingBlockEntitySmoke.run(level));
        passed.addAll(ClassicSpecializedSurfaceSmoke.run(server,level));
        long changed=0;
        for(int chunkX=-1;chunkX<=1;chunkX++){
            var first=new ProtoChunk(new ChunkPos(chunkX,-1),UpgradeData.EMPTY,level,biomes,null);var second=new ProtoChunk(first.getPos(),UpgradeData.EMPTY,level,biomes,null);
            ((net.minecraft.world.level.chunk.ChunkGenerator) generator).fillFromNoise(Runnable::run,Blender.empty(),null,null,first).join();((net.minecraft.world.level.chunk.ChunkGenerator) generator).fillFromNoise(Runnable::run,Blender.empty(),null,null,second).join();
            var cave=new ClassicCaves(987654321L,15,40,Blocks.AIR);cave.generate(first);new ClassicCaves(987654321L,15,40,Blocks.AIR).generate(second);
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<64;y++){
                var pos=new BlockPos(first.getPos().getMinBlockX()+x,y,first.getPos().getMinBlockZ()+z);
                check(first.getBlockState(pos).equals(second.getBlockState(pos)),"carving deterministic across independent instances");
                if(y==0)check(first.getBlockState(pos).is(Blocks.BEDROCK),"source carver preserves bedrock");else if(first.getBlockState(pos).isAir())changed++;
            }
        }
        check(changed>0,"source cave traversal carves actual stone chunks");
        passed.add("Generation: real source cave traversal carves stone, preserves bedrock and reproduces independent chunk instances");
        return passed;
    }
    private static CompoundTag page(String path){CompoundTag tag=new CompoundTag();tag.putString("symbol","mystcraft:"+path);return tag;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private ClassicGenerationSmoke(){}
}
