package net.mystcraft.smoke;

import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.mystcraft.minecraft.age.generation.ClassicChunkGenerator;
import net.mystcraft.minecraft.age.generation.ClassicTerrainSpec;

/** Registry-backed v4 surface dispatch and retained frozen-v3 behavior, without live chunk loads. */
public final class ClassicSpecializedSurfaceSmoke {
    private ClassicSpecializedSurfaceSmoke(){}
    public static List<String> run(MinecraftServer server,ServerLevel level){
        var biomes=server.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        var structures=server.registryAccess().registryOrThrow(Registry.STRUCTURE_SET_REGISTRY);
        for(var biome:List.of(Biomes.BADLANDS,Biomes.SWAMP)){
            var source=new FixedBiomeSource(biomes.getHolderOrThrow(biome));
            var oldSpec=new ClassicTerrainSpec(3,"normal",new ResourceLocation("minecraft:stone"),new ResourceLocation("minecraft:water"),64,63,true);
            var newSpec=new ClassicTerrainSpec(4,"normal",oldSpec.fill(),oldSpec.sea(),64,63,true);
            var oldChunk=columnChunk(level,source,biome.equals(Biomes.SWAMP)?62:95);
            var newChunk=columnChunk(level,source,biome.equals(Biomes.SWAMP)?62:95);
            ((ChunkGenerator)new ClassicChunkGenerator(structures,source,123456789,oldSpec)).buildSurface(null,null,null,oldChunk);
            ((ChunkGenerator)new ClassicChunkGenerator(structures,source,123456789,newSpec)).buildSurface(null,null,null,newChunk);
            int oldMarkers=0,newMarkers=0;
            for(var pos:net.minecraft.core.BlockPos.betweenClosed(0,0,0,15,127,15)){
                var before=oldChunk.getBlockState(pos);var after=newChunk.getBlockState(pos);
                if(biome.equals(Biomes.SWAMP)){
                    if(before.is(Blocks.WATER)||before.is(Blocks.LILY_PAD))oldMarkers++;
                    if(after.is(Blocks.WATER)||after.is(Blocks.LILY_PAD))newMarkers++;
                }else{
                    if(before.is(Blocks.TERRACOTTA)||before.is(Blocks.ORANGE_TERRACOTTA)||before.is(Blocks.WHITE_TERRACOTTA))oldMarkers++;
                    if(after.is(Blocks.TERRACOTTA)||after.is(Blocks.ORANGE_TERRACOTTA)||after.is(Blocks.WHITE_TERRACOTTA))newMarkers++;
                }
            }
            check(oldMarkers==0&&newMarkers>0,"version4 must add specialized "+biome.location()+" surfaces while frozen3 retains its original default builder");
        }
        return List.of("Generation: real registry/chunk version4 Mesa strata and swamp pools execute; frozen version3 retains original surfaces without world chunk loads");
    }
    private static ProtoChunk columnChunk(ServerLevel level,FixedBiomeSource source,int height){
        var chunk=new ProtoChunk(new ChunkPos(0,0),UpgradeData.EMPTY,level,level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY),null);
        ((ChunkAccess)chunk).fillBiomesFromNoise(source,Climate.empty());
        // Vanilla marks BIOMES complete only after filling section biome palettes.
        // Keep below FEATURES so this geometry fixture does not enqueue lighting work.
        chunk.setStatus(net.minecraft.world.level.chunk.ChunkStatus.BIOMES);
        for(var pos:net.minecraft.core.BlockPos.betweenClosed(0,0,0,15,height,15))chunk.setBlockState(pos,Blocks.STONE.defaultBlockState(),false);
        return chunk;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
