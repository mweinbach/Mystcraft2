package net.mystcraft.minecraft.instability;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

/** Classic 256-layer cell-frequency profiler; never obtains or generates another chunk. */
public final class InstabilityProfile {
    public record Factors(float accessible, float total) {}
    public static final Map<String, Factors> FACTORS = new LinkedHashMap<>();
    static { resetFactors(); }
    public static void resetFactors() {
        FACTORS.clear();
        watch(Blocks.COAL_ORE, 5, 1); watch(Blocks.LAPIS_ORE, 5, 1); watch(Blocks.IRON_ORE, 60, 1);
        watch(Blocks.EMERALD_ORE, 200, 2); watch(Blocks.REDSTONE_ORE, 250, 2);
        watch(Blocks.GOLD_ORE, 750, 4); watch(Blocks.DIAMOND_ORE, 4000, 20);
        watch(Blocks.GLOWSTONE, 50, 4); watch(Blocks.NETHER_QUARTZ_ORE, 20, 4);
        FACTORS.put("mystcraft:crystal", new Factors(20, 4));
    }
    private static void watch(Block block, float a, float t) { FACTORS.put(Registry.BLOCK.getKey(block).toString(), new Factors(a,t)); }
    private final Set<Long> chunks = new HashSet<>();
    private final int[] solid = new int[65536];
    private final Map<String, int[]> watched = new LinkedHashMap<>();
    // Allocate a dense cell array only after a watched block occurs. Many mod fluids are registered
    // but absent from a given Age; eagerly allocating256KiB per fluid per Age is unnecessary.
    public InstabilityProfile() {}
    public int count() { return chunks.size(); }
    public boolean contains(long chunk) { return chunks.contains(chunk); }
    public boolean profile(LevelChunk chunk) {
        if (!chunks.add(chunk.getPos().toLong())) return false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        for (int y = 0; y < 256; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) continue;
            pos.set(baseX+x,y,baseZ+z);
            var state = chunk.getBlockState(pos);
            int index = y << 8 | z << 4 | x;
            String key = Registry.BLOCK.getKey(state.getBlock()).toString();
            int[] map = FACTORS.containsKey(key) ? watched.computeIfAbsent(key, ignored -> new int[65536]) : null;
            if (map != null) ++map[index];
            // Legacy passability has no state-only modern equivalent: empty collision is its adapter.
            solid[index] += state.isAir() ? 0 : map != null || state.getCollisionShape(chunk, pos).isEmpty() ? 1 : 2;
        }
        return true;
    }
    public Map<String, Float> split() {
        Map<String, Float> split = new LinkedHashMap<>();
        if (count() < 100) return split;
        float[] averages = new float[256], rounded = new float[256];
        float minimum=1, groundSum=0; int groundCount=0;
        for (int y=0;y<256;y++) {
            for(int cell=0;cell<256;cell++) averages[y] += solid[(y<<8)|cell] / (float)(count()*2);
            averages[y]/=256; minimum=Math.min(minimum,averages[y]);
        }
        for(int y=0;y<256;y++) {
            rounded[y]=Math.round(100*Math.max(0,averages[y]-minimum))/100F;
            if(rounded[y]>0) { groundSum+=rounded[y]; groundCount++; }
        }
        float ground=groundCount==0 ? 0 : groundSum/groundCount;
        for(var entry:watched.entrySet()) {
            Factors factors=FACTORS.get(entry.getKey()); if(factors==null) continue; float sum=0;
            for(int y=0;y<256;y++) {
                float availability=rounded[y]>ground ? 1-rounded[y] : 1;
                for(int cell=0;cell<256;cell++) {
                    float frequency=entry.getValue()[(y<<8)|cell]/(float)count();
                    sum+=frequency*availability*factors.accessible()+frequency*factors.total();
                }
            }
            split.put(entry.getKey(),sum);
        }
        return split;
    }
    public int score(Map<String, Integer> baseline) {
        if(baseline==null || count()<=400) return 0;
        float sum=0;
        for(var entry:split().entrySet()) {
            float value=entry.getValue();
            sum+=value>0 ? Math.max(0,value-baseline.getOrDefault(entry.getKey(),0)) : value;
        }
        return Math.round(sum);
    }
    public static CompoundTag watcherSignature() {
        CompoundTag result=new CompoundTag();
        FACTORS.forEach((key,factors)->{CompoundTag value=new CompoundTag();value.putFloat("Accessible",factors.accessible());value.putFloat("Total",factors.total());result.put(key,value);});
        return result;
    }
    public static boolean compatible(CompoundTag tag) {
        if(tag.contains("Watchers",10))return tag.getCompound("Watchers").equals(watcherSignature());
        // Previous format eagerly wrote every watcher, including all-zero arrays.
        return tag.getCompound("Blocks").getAllKeys().equals(FACTORS.keySet());
    }
    public CompoundTag save() {
        CompoundTag tag=new CompoundTag(); tag.put("Watchers",watcherSignature()); tag.putLongArray("Chunks",chunks.stream().mapToLong(Long::longValue).sorted().toArray());
        tag.putIntArray("Solid",solid); CompoundTag maps=new CompoundTag(); watched.forEach(maps::putIntArray); tag.put("Blocks",maps); return tag;
    }
    public static InstabilityProfile load(CompoundTag tag) {
        InstabilityProfile result=new InstabilityProfile();
        if(!compatible(tag))return result;
        int[] saved=tag.getIntArray("Solid"); if(saved.length!=65536) return result;
        System.arraycopy(saved,0,result.solid,0,65536);
        for(long chunk:tag.getLongArray("Chunks")) result.chunks.add(chunk);
        CompoundTag blocks=tag.getCompound("Blocks");
        for(String key:blocks.getAllKeys()) {
            if(!FACTORS.containsKey(key)) continue;
            int[] data=blocks.getIntArray(key); if(data.length==65536) result.watched.put(key,data.clone());
        }
        return result;
    }
}
