package net.mystcraft.smoke;

import net.mystcraft.minecraft.age.generation.ClassicSkylands;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;

/** Native pending-DUMMY lifecycle regression without touching a live world chunk. */
public final class SkylandsPendingBlockEntitySmoke {
    private SkylandsPendingBlockEntitySmoke() {}
    public static List<String> run(ServerLevel level) {
        var chunk=new ProtoChunk(new ChunkPos(0,0),UpgradeData.EMPTY,level,
                level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY),null);
        BlockPos removed=new BlockPos(0,1,0), retained=new BlockPos(0,250,0), live=new BlockPos(0,2,0);
        for(BlockPos pos:List.of(removed,retained,live))chunk.setBlockState(pos,Blocks.CHEST.defaultBlockState(),false);
        for(BlockPos pos:List.of(removed,retained)){
            CompoundTag tag=new CompoundTag();tag.putString("id","DUMMY");
            tag.putInt("x",pos.getX());tag.putInt("y",pos.getY());tag.putInt("z",pos.getZ());
            tag.putString("FixturePayload","retain exact surviving structure data");
            chunk.setBlockEntityNbt(tag);
        }
        CompoundTag original=chunk.getBlockEntityNbt(retained).copy();
        chunk.setBlockEntity(new ChestBlockEntity(live,Blocks.CHEST.defaultBlockState()));
        ClassicSkylands.filter(chunk,0);
        check(chunk.getBlockState(removed).isAir()&&chunk.getBlockEntityNbt(removed)==null,
                "removed generated chest must not retain a packed DUMMY entry");
        check(chunk.getBlockState(live).isAir()&&chunk.getBlockEntity(live)==null,
                "removed generated chest must not retain a live block entity");
        check(chunk.getBlockState(retained).is(Blocks.CHEST)&&original.equals(chunk.getBlockEntityNbt(retained)),
                "surviving structure block and exact pending data must remain unchanged");
        return List.of("Generation: native ProtoChunk Skylands removal discards pending DUMMY/live block entities while preserving surviving exact structure NBT");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
