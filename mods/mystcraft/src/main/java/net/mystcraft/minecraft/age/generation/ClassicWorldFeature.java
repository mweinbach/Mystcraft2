package net.mystcraft.minecraft.age.generation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;
/** Modern write boundary for the pinned classic feature algorithms. */
abstract class ClassicWorldFeature {
    abstract boolean doGeneration(WorldGenLevel level,RandomSource random,BlockPos origin);
    final boolean placeBlock(WorldGenLevel level,BlockPos pos,BlockState state,int flags){
        if(level.isOutsideBuildHeight(pos)||!DeferredCrystalWrites.canWriteImmediately(level,pos)||!level.ensureCanWrite(pos)||level.getBlockEntity(pos)!=null)return false;
        return level.setBlock(pos,state,flags);
    }
}
