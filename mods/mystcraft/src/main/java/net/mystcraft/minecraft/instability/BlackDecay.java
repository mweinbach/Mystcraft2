package net.mystcraft.minecraft.instability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.mystcraft.integration.registry.DecayRegistry;

/** Coherent source black pulse: 1/10 subsidence; otherwise 1/5 horizontal corruption. */
public final class BlackDecay {
    private BlackDecay() {}
    public static int pulse(ServerLevel level,BlockPos pos,RandomSource random) {
        if(!DecayPropagation.isAge(level) || !level.hasChunkAt(pos)) return 0;
        if(random.nextInt(10)==0) { int converted=corruptNeighbors(level,pos); fall(level,pos); return converted; }
        return random.nextInt(5)==0?corruptNeighbors(level,pos):0;
    }
    private static int corruptNeighbors(ServerLevel level,BlockPos pos) {
        int converted=0;
        for(Direction direction:Direction.Plane.HORIZONTAL) if(corrupt(level,pos.relative(direction))) converted++;
        return converted;
    }
    public static boolean corrupt(ServerLevel level,BlockPos pos) {
        if(level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return false;
        BlockState state=level.getBlockState(pos);
        if(state.getMaterial().isLiquid()) return level.setBlock(pos,Blocks.AIR.defaultBlockState(),DecayPropagation.CONVERSION_FLAGS);
        if(state.isAir() || state.is(DecayRegistry.block(DecayType.BLACK).get())) return false;
        return level.setBlock(pos,DecayRegistry.block(DecayType.BLACK).get().defaultBlockState(),DecayPropagation.CONVERSION_FLAGS);
    }
    public static boolean fall(ServerLevel level,BlockPos pos) {
        BlockPos below=pos.below();
        if(level.isOutsideBuildHeight(below) || !level.hasChunkAt(below)) return false;
        BlockState support=level.getBlockState(below); BlockEntity blockEntity=level.getBlockEntity(below);
        CompoundTag saved=blockEntity==null?null:blockEntity.saveWithFullMetadata();
        if(blockEntity!=null) level.removeBlockEntity(below);
        if(!support.isAir() && !level.setBlock(below,Blocks.AIR.defaultBlockState(),DecayPropagation.CONVERSION_FLAGS)) {
            restore(level,below,support,saved); return false;
        }
        if(DecayFallingBlockEntity.drop(level,pos)) return true;
        restore(level,below,support,saved); return false;
    }
    private static void restore(ServerLevel level,BlockPos pos,BlockState state,CompoundTag data) {
        level.setBlock(pos,state,DecayPropagation.CONVERSION_FLAGS);
        if(data!=null) { BlockEntity restored=BlockEntity.loadStatic(pos,state,data); if(restored!=null) level.setBlockEntity(restored); }
    }
}
