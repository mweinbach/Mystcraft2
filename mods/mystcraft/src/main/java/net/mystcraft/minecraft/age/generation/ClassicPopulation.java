package net.mystcraft.minecraft.age.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Source SymbolDungeons eight attempts and SymbolObelisks geometry; modern dungeon content adapter. */
final class ClassicPopulation {
    private ClassicPopulation(){}
    static void populate(WorldGenLevel level,ChunkGenerator generator,ChunkPos chunk,long seed,ClassicGenerationPlan plan,net.minecraft.world.level.StructureManager structures){
        var random=new LegacyRandomSource(seed);long x=random.nextLong()/2*2+1,z=random.nextLong()/2*2+1;
        random.setSeed(chunk.x*x+chunk.z*z^seed);
        for(var feature:plan.alterations()){
            // Source AgeController's flag || populate short-circuits all remaining populators
            // once a village returns true, rather than merely informing the next callback.
            if(feature.kind().equals("villages")){
                var registry=level.registryAccess().registryOrThrow(net.minecraft.core.Registry.STRUCTURE_REGISTRY);
                boolean village=structures.startsForStructure(chunk,structure->{var id=registry.getKey(structure);return id!=null&&id.getNamespace().equals("minecraft")&&id.getPath().startsWith("village_");}).stream()
                        .anyMatch(start->start.isValid()&&start.getBoundingBox().intersects(chunk.getMinBlockX()+8,chunk.getMinBlockZ()+8,chunk.getMinBlockX()+23,chunk.getMinBlockZ()+23));
                if(village)break;
            }
            if(feature.kind().equals("dungeons")){
                for(int attempt=0;attempt<8;attempt++){
                    int px=chunk.getMinBlockX()+random.nextInt(16)+8,py=random.nextInt(256),pz=chunk.getMinBlockZ()+random.nextInt(16)+8;
                    Feature.MONSTER_ROOM.place(NoneFeatureConfiguration.INSTANCE,level,generator,random,new BlockPos(px,py,pz));
                }
            }else if(feature.kind().equals("lakessurface")&&random.nextInt(4)==0){
                int px=chunk.getMinBlockX()+random.nextInt(16)+8,py=random.nextInt(256),pz=chunk.getMinBlockZ()+random.nextInt(16)+8;
                new ClassicLakes(feature.state()).doGeneration(level,random,new BlockPos(px,py,pz));
            }else if(feature.kind().equals("lakesdeep")&&random.nextInt(8)==0){
                int px=chunk.getMinBlockX()+random.nextInt(16)+8,py=random.nextInt(random.nextInt(248)+8),pz=chunk.getMinBlockZ()+random.nextInt(16)+8;
                if(py<plan.terrain().seaLevel()||random.nextInt(10)==0)new ClassicLakes(feature.state()).doGeneration(level,random,new BlockPos(px,py,pz));
            }else if(feature.kind().equals("genspikes")&&random.nextInt(18)==0){
                int px=chunk.getMinBlockX()+random.nextInt(16),pz=chunk.getMinBlockZ()+random.nextInt(16);
                new ClassicSpikes(feature.state()).doGeneration(level,random,new BlockPos(px,level.getHeight(Heightmap.Types.MOTION_BLOCKING,px,pz),pz));
            }else if(feature.kind().equals("cryform")&&random.nextInt(15)==0){
                int px=chunk.getMinBlockX()+random.nextInt(16)+8,pz=chunk.getMinBlockZ()+random.nextInt(16)+8;
                new ClassicCrystalFormation(feature.state()).doGeneration(level,random,new BlockPos(px,0,pz));
            }else if(feature.kind().equals("denseores")){
                dense(level,generator,chunk,random);
            }else if(feature.kind().equals("obelisks")&&random.nextInt(128)==0){
                int px=chunk.getMinBlockX()+random.nextInt(16),pz=chunk.getMinBlockZ()+random.nextInt(16);
                int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING,px,pz);
                if(y>0)obelisk(level,new BlockPos(px,y,pz),feature.state());
            }
        }
    }
    private static void dense(WorldGenLevel level,ChunkGenerator generator,ChunkPos chunk,net.minecraft.util.RandomSource random){
        net.minecraft.world.level.block.Block[] ores={Blocks.COAL_ORE,Blocks.IRON_ORE,Blocks.GOLD_ORE,Blocks.REDSTONE_ORE,Blocks.DIAMOND_ORE,Blocks.LAPIS_ORE,Blocks.EMERALD_ORE,Blocks.NETHER_QUARTZ_ORE};
        int[] attempts={20,20,2,8,1,1,6,10},sizes={16,8,8,7,7,6,1,13},minimum={0,0,0,0,0,0,4,10},maximum={128,64,32,16,16,16,32,256};
        for(int ore=0;ore<ores.length;ore++){
            var target=new net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest(ore==7?Blocks.NETHERRACK:Blocks.STONE);
            var config=new net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration(target,ores[ore].defaultBlockState(),sizes[ore]);
            for(int attempt=0;attempt<attempts[ore];attempt++){
                int x=chunk.getMinBlockX()+random.nextInt(16),y=random.nextInt(maximum[ore]-minimum[ore])+minimum[ore],z=chunk.getMinBlockZ()+random.nextInt(16);
                Feature.ORE.place(config,level,generator,random,new BlockPos(x,y,z));
            }
        }
    }
    static void obelisk(WorldGenLevel level,BlockPos height,BlockState material){
        BlockPos base=height.above();
        // Avoid damaging modern structure block entities, and never generate outside the region bounds.
        for(int y=-4;y<12;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++){
            BlockPos at=base.offset(x,y,z);if(at.getY()<level.getMinBuildHeight()||at.getY()>=level.getMaxBuildHeight()||level.getBlockEntity(at)!=null)return;
        }
        boolean found=false;
        for(int y=base.getY();y>base.getY()-5&&!found;y--){
            found=true;
            for(int z=0;z<4;z++)for(int x=0;x<4;x++){
                BlockPos at=new BlockPos(base.getX()+x,y,base.getZ()+z);level.setBlock(at,material,2);
                BlockState below=level.getBlockState(at.below());
                if(below.isAir()||below.is(Blocks.SNOW_BLOCK)||below.getMaterial().isLiquid())found=false;
            }
        }
        base=base.offset(1,0,1);
        for(int y=0;y<12;y++)for(int z=0;z<2;z++)for(int x=0;x<2;x++)level.setBlock(base.offset(x,y,z),material,2);
    }
}
