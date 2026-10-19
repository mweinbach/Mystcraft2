package net.mystcraft.minecraft.age.generation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
/** SymbolSkylands source seven-octave cutoff; no invented Y shift or replacement terrain. */
public final class ClassicSkylands {
    public static void filter(ChunkAccess chunk,long seed){
        double[] noise=new ClassicOctaveNoise(new LegacyRandomSource(seed),7).field(chunk.getPos().getMinBlockX(),10,chunk.getPos().getMinBlockZ(),16,1,16,1,1,1);
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<256;y++){
            pos.set(chunk.getPos().getMinBlockX()+x,y,chunk.getPos().getMinBlockZ()+z);
            var state=chunk.getBlockState(pos);if(!state.isAir()&&(y<=76+(int)noise[x*16+z]||state.getMaterial().isLiquid())){
                // ProtoChunk writes do not discard packed structure block-entity NBT.
                // Remove both packed/live entries before the source filter clears their blocks.
                chunk.removeBlockEntity(pos);
                chunk.setBlockState(pos,Blocks.AIR.defaultBlockState(),false);
            }
        }
    }
    private ClassicSkylands(){}
}
