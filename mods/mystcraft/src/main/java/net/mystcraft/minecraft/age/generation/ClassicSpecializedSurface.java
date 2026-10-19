package net.mystcraft.minecraft.age.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Version-four native-biome override adapter. Unknown/modded IDs retain their default surface. */
final class ClassicSpecializedSurface {
    // Rules remain separate from block registries for independent numerical/golden tests.

    private final ClassicMesaSurface mesa;
    private final ClassicSwampSurface swamp=new ClassicSwampSurface();
    private static final BlockState[] MESA_STATES={Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState(),Blocks.BEDROCK.defaultBlockState(),Blocks.WATER.defaultBlockState(),
            Blocks.TERRACOTTA.defaultBlockState(),Blocks.WHITE_TERRACOTTA.defaultBlockState(),Blocks.ORANGE_TERRACOTTA.defaultBlockState(),Blocks.YELLOW_TERRACOTTA.defaultBlockState(),
            Blocks.BROWN_TERRACOTTA.defaultBlockState(),Blocks.RED_TERRACOTTA.defaultBlockState(),Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(),Blocks.RED_SAND.defaultBlockState(),
            Blocks.COARSE_DIRT.defaultBlockState(),Blocks.GRASS_BLOCK.defaultBlockState(),Blocks.WHITE_TERRACOTTA.defaultBlockState()};
    ClassicSpecializedSurface(long seed){mesa=new ClassicMesaSurface(seed);}
    static BlockState material(ClassicSurfaceRules.Material material){
        return switch(material){
            case GRASS->Blocks.GRASS_BLOCK.defaultBlockState();case DIRT->Blocks.DIRT.defaultBlockState();
            case COARSE_DIRT->Blocks.COARSE_DIRT.defaultBlockState();case PODZOL->Blocks.PODZOL.defaultBlockState();
            case GRAVEL->Blocks.GRAVEL.defaultBlockState();case STONE->Blocks.STONE.defaultBlockState();
        };
    }
    boolean column(ChunkAccess chunk,RandomSource random,int x,int z,int sea,double noise,ClassicSurfaceRules.Kind kind){
        if(kind!=ClassicSurfaceRules.Kind.MESA&&kind!=ClassicSurfaceRules.Kind.BRYCE&&kind!=ClassicSurfaceRules.Kind.WOODED_MESA)return false;
        var pos=new BlockPos.MutableBlockPos();
        mesa.column(new ClassicMesaSurface.Column(){
            public int get(int y){BlockState state=chunk.getBlockState(pos.set(x,y,z));return state.isAir()?ClassicMesaSurface.AIR:state.is(Blocks.STONE)?ClassicMesaSurface.STONE:-1;}
            public void set(int y,int state){chunk.setBlockState(pos.set(x,y,z),MESA_STATES[state],false);}
        },random,x,z,sea,noise,kind==ClassicSurfaceRules.Kind.BRYCE,kind==ClassicSurfaceRules.Kind.WOODED_MESA);
        return true;
    }
    void swamp(ChunkAccess chunk,int x,int z){
        var pos=new BlockPos.MutableBlockPos();
        swamp.column(new ClassicSwampSurface.Column(){
            public int get(int y){var state=chunk.getBlockState(pos.set(x,y,z));return state.isAir()?ClassicSwampSurface.AIR:state.is(Blocks.WATER)?ClassicSwampSurface.WATER:-1;}
            public void set(int y,int state){chunk.setBlockState(pos.set(x,y,z),(state==ClassicSwampSurface.WATER?Blocks.WATER:Blocks.LILY_PAD).defaultBlockState(),false);}
        },x,z);
    }
}
