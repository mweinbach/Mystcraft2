package net.mystcraft.minecraft.age.generation;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Mechanical adapter of pinned classic MapGenAdvanced; no shared mutable RNG between chunks. */
public abstract class ClassicCarverBase {
    protected final int range=8;
    protected final Random rand=new Random();
    private final long seed;
    private final BlockState replacement;
    ClassicCarverBase(long seed,BlockState replacement){this.seed=seed;this.replacement=replacement;}
    public final void generate(ChunkAccess chunk){
        int chunkX=chunk.getPos().x,chunkZ=chunk.getPos().z;
        rand.setSeed(seed);long xs=rand.nextLong(),zs=rand.nextLong();
        for(int x=chunkX-range;x<=chunkX+range;x++)for(int z=chunkZ-range;z<=chunkZ+range;z++){
            rand.setSeed(x*xs^z*zs^seed);recursiveGenerate(x,z,chunkX,chunkZ,chunk);
        }
    }
    protected abstract void recursiveGenerate(int x,int z,int chunkX,int chunkZ,ChunkAccess primer);
    protected final boolean placeBlock(ChunkAccess primer,int x,int y,int z){
        BlockPos pos=new BlockPos(primer.getPos().getMinBlockX()+x,y,primer.getPos().getMinBlockZ()+z);
        BlockState current=primer.getBlockState(pos);
        if(current.is(Blocks.BEDROCK)||(!replacement.getMaterial().isSolid()&&current.getMaterial().isLiquid()))return false;
        primer.setBlockState(pos,replacement,false);return true;
    }
    protected final BlockState state(ChunkAccess primer,int x,int y,int z){
        return primer.getBlockState(new BlockPos(primer.getPos().getMinBlockX()+x,y,primer.getPos().getMinBlockZ()+z));
    }
}
