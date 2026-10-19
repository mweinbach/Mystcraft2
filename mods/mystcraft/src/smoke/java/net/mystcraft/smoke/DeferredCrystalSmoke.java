package net.mystcraft.smoke;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.mystcraft.integration.registry.PortalRegistry;
import net.mystcraft.minecraft.age.AgeManager;
import net.mystcraft.minecraft.age.AgeSavedData;
import net.mystcraft.minecraft.age.AgeServerLevel;
import net.mystcraft.minecraft.age.generation.DeferredCrystalWrites;
import net.mystcraft.minecraft.age.generation.RegionWriteBounds;
import net.mystcraft.minecraft.exploration.ClassicCrystalFeature;

/** Real generation-region boundary + canonical queue restart fixture; no neighbor is forced by ingress/replay. */
public final class DeferredCrystalSmoke {
    private static final String FIXTURE = "SmokeDeferredCrystal";
    private DeferredCrystalSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel ignored) {
        AgeSavedData.Entry entry = AgeManager.data(server).entries().stream().sorted(java.util.Comparator.comparingLong(AgeSavedData.Entry::number))
                .filter(age -> server.getLevel(ResourceKey.create(Registry.DIMENSION_REGISTRY, age.dimensionId())) instanceof AgeServerLevel).findFirst().orElseThrow();
        ServerLevel level = server.getLevel(ResourceKey.create(Registry.DIMENSION_REGISTRY, entry.dimensionId()));
        AgeServerLevel age = (AgeServerLevel) level;
        int run = entry.state().getInt("SmokeDeferredRun") + 1;
        check(run <= 1024, "deferred fixture coordinate budget exhausted");
        entry.state().putInt("SmokeDeferredRun", run);
        int coordinate = 10000 + run * 32;
        ChunkPos center = new ChunkPos(coordinate,coordinate);
        WorldGenRegion expected = region(level,center,2), bounded = region(level,center,1);
        check(bounded instanceof RegionWriteBounds, "real loader accessor must expose native WorldGenRegion write radius");
        BlockPos root = new BlockPos(center.getMinBlockX()+31,80,center.getMinBlockZ()+8);
        var expectedRandom=RandomSource.create(1); var actualRandom=RandomSource.create(1);
        var feature = new ClassicCrystalFeature(); feature.generateAt(expected,root,expectedRandom); feature.generateAt(bounded,root,actualRandom);
        check(expectedRandom.nextLong()==actualRandom.nextLong(),"deferral must not consume or change source geometry RNG");
        Map<BlockPos,BlockState> outside=new LinkedHashMap<>();
        for(BlockPos pos:BlockPos.betweenClosed(root.offset(-15,-1,-5),root.offset(15,15,5))){
            if(!expected.getBlockState(pos).is(PortalRegistry.CRYSTAL.get()))continue;
            if(DeferredCrystalWrites.withinRadius(center,pos,1))check(bounded.getBlockState(pos).equals(expected.getBlockState(pos)),"in-region geometry differs");
            else outside.put(pos.immutable(),expected.getBlockState(pos));
        }
        check(!outside.isEmpty(),"fixture must actually cross the x32 modern write boundary");
        for(BlockPos pos:outside.keySet())check(!level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4),"fixture destination must initially be unloaded");
        CompoundTag snapshot=onlySource(age.deferredCrystalWrites().snapshot(level),center.toLong());
        check(snapshot.getList("Writes",Tag.TAG_COMPOUND).size()==outside.size(),"every out-of-region geometry voxel must be retained once");
        DeferredCrystalWrites reloaded=DeferredCrystalWrites.fromSnapshot(level,snapshot);
        check(reloaded.snapshot(level).equals(snapshot),"restart serialization must retain exact states/positions/order metadata");
        reloaded.tick(level);
        check(reloaded.pendingCount()==outside.size(),"replay must defer when destination is not a loaded FULL chunk");
        for(BlockPos pos:outside.keySet())check(!level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4),"queue/replay must not load its destination chunk");
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        BlockPos chestPos=outside.keySet().iterator().next(), bedrockPos=outside.keySet().stream().skip(1).findFirst().orElseThrow();
        try{
            for(BlockPos pos:outside.keySet()){
                level.getChunk(pos.getX()>>4,pos.getZ()>>4);
                check(level.getBlockEntity(pos)==null,"deferred smoke must preserve existing block entities");before.put(pos,level.getBlockState(pos));
            }
            level.setBlock(chestPos,Blocks.CHEST.defaultBlockState(),18);
            ChestBlockEntity chest=(ChestBlockEntity)level.getBlockEntity(chestPos);check(chest!=null,"fixture chest exists");
            chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,11));
            level.setBlock(bedrockPos,Blocks.BEDROCK.defaultBlockState(),18);
            reloaded.tick(level);age.deferredCrystalWrites().tick(level);
            check(reloaded.pendingCount()==0,"loaded FULL target must receive all unprotected queued geometry");
            for(var write:outside.entrySet())if(!write.getKey().equals(chestPos)&&!write.getKey().equals(bedrockPos))
                check(level.getBlockState(write.getKey()).equals(write.getValue()),"deferred geometry lost at "+write.getKey());
            check(level.getBlockEntity(chestPos)==chest&&chest.getItem(0).getCount()==11&&level.getBlockState(bedrockPos).is(Blocks.BEDROCK),"foreign inventory and bedrock must survive replay");
        }finally{
            if(level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)chest.clearContent();
            for(var restored:before.entrySet())level.setBlock(restored.getKey(),restored.getValue(),18);
        }
        // Keep a different unloaded destination pending for actual process-restart verification.
        ChunkPos restartCenter=new ChunkPos(coordinate+10,coordinate+10);
        feature.generateAt(region(level,restartCenter,1),new BlockPos(restartCenter.getMinBlockX()+31,80,restartCenter.getMinBlockZ()+8),RandomSource.create(1));
        CompoundTag restart=onlySource(age.deferredCrystalWrites().snapshot(level),restartCenter.toLong());
        check(!restart.getList("Writes",Tag.TAG_COMPOUND).isEmpty(),"restart fixture requires pending geometry");
        entry.state().put(FIXTURE,restart);((net.minecraft.world.level.saveddata.SavedData) AgeManager.data(server)).setDirty();age.deferredCrystalWrites().flushForSave(level);
        return List.of("deferred crystal: actual native generation-region boundary preserves complete source geometry and RNG without far-chunk writes or destination loads",
                "deferred crystal: exact NBT reload replays only loaded FULL chunks while preserving foreign chest inventory and bedrock",
                "deferred crystal: pending canonical queue saved for actual JVM restart verification");
    }
    public static List<String> verifyFixture(MinecraftServer server){
        int verified=0;
        for(var entry:AgeManager.data(server).entries()){
            if(!entry.state().contains(FIXTURE,Tag.TAG_COMPOUND))continue;
            ServerLevel level=server.getLevel(ResourceKey.create(Registry.DIMENSION_REGISTRY,entry.dimensionId()));
            check(level instanceof AgeServerLevel,"restart must restore pending queue's Age before verification");
            var queue=((AgeServerLevel)level).deferredCrystalWrites();
            CompoundTag fixture=entry.state().getCompound(FIXTURE);var writes=fixture.getList("Writes",Tag.TAG_COMPOUND);
            long source=writes.getCompound(0).getLong("Source");
            check(onlySource(queue.snapshot(level),source).equals(fixture),"actual JVM restart must retain all pending geometry");
            for(int i=0;i<writes.size();i++){
                BlockPos pos=BlockPos.of(writes.getCompound(i).getLong("Pos"));
                check(!level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4),"startup must not force deferred target chunks");
            }
            entry.state().remove(FIXTURE);((net.minecraft.world.level.saveddata.SavedData) AgeManager.data(server)).setDirty();verified++;
        }
        check(verified>0,"no deferred crystal restart fixture was saved in the prior JVM");
        return List.of("deferred crystal restart: canonical pending geometry survived actual JVM restart without loading destination chunks");
    }
    private static CompoundTag onlySource(CompoundTag snapshot,long source){
        ListTag selected=new ListTag();var writes=snapshot.getList("Writes",Tag.TAG_COMPOUND);
        for(int i=0;i<writes.size();i++)if(writes.getCompound(i).getLong("Source")==source)selected.add(writes.getCompound(i).copy());
        CompoundTag result=new CompoundTag();result.putInt("Version",1);result.put("Writes",selected);return result;
    }
    private static WorldGenRegion region(ServerLevel level,ChunkPos center,int radius){
        List<ChunkAccess> chunks=new ArrayList<>();
        Map<Long,ProtoChunk> lightingChunks=new java.util.HashMap<>();
        for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++){
            ProtoChunk chunk=new ProtoChunk(new ChunkPos(center.x+x,center.z+z),UpgradeData.EMPTY,level,
                    level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY),null);
            chunks.add(chunk);lightingChunks.put(chunk.getPos().toLong(),chunk);
        }
        // Geometry-only fixtures must never enqueue synthetic far positions into the live
        // ThreadedLevelLightEngine. Both light layers are unnecessary for voxel/RNG proof.
        var lightEngine=new net.minecraft.world.level.lighting.LevelLightEngine(
                new net.minecraft.world.level.chunk.LightChunkGetter(){
                    @Override public net.minecraft.world.level.BlockGetter getChunkForLighting(int x,int z){
                        return lightingChunks.get(ChunkPos.asLong(x,z));
                    }
                    @Override public net.minecraft.world.level.BlockGetter getLevel(){return level;}
                },false,false);
        for(ChunkAccess chunk:chunks){
            ((ProtoChunk)chunk).setLightEngine(lightEngine);
            ((ProtoChunk)chunk).setStatus(ChunkStatus.FEATURES);
        }
        if(radius==2){
            // The reference oracle deliberately permits the entire synthetic cache.
            // BCLib replaces native ensureCanWrite with a fixed radius-one policy,
            // ignoring the constructor radius; that must not truncate our oracle.
            // The actual radius-one test region below retains every native/mod hook.
            return new WorldGenRegion(level,chunks,ChunkStatus.FEATURES,radius){
                @Override public boolean ensureCanWrite(BlockPos position){
                    return DeferredCrystalWrites.withinRadius(getCenter(),position,radius)
                            && !isOutsideBuildHeight(position);
                }
            };
        }
        return new WorldGenRegion(level,chunks,ChunkStatus.FEATURES,radius);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
