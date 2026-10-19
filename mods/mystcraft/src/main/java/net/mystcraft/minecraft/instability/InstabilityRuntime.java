package net.mystcraft.minecraft.instability;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/** Classic scoring/deck coordinator. Called only for already ticking chunks, never generates its own samples. */
public final class InstabilityRuntime {
    private static final Map<ServerLevel,InstabilityRuntime> RUNTIMES=new IdentityHashMap<>();
    private final InstabilityProfile profile;
    private final CompoundTag data;
    private final CompoundTag decks;
    private int blockScore;
    private int scoredAt;
    private boolean hasBlockScore;
    private int baselineHash;
    private static BiConsumer<ServerLevel,Vec3> meteorSpawner;
    private InstabilityRuntime(ServerLevel level,CompoundTag ageState) {
        data=ageState.getCompound("Instability"); ageState.put("Instability",data);
        profile=InstabilityProfile.load(data.getCompound("Profile"));
        data.put("ExplicitLightning",ageState.getList("ExplicitLightning",10).copy());
        decks=InstabilityDecks.reconcile(level.getSeed(),data.getCompound("Decks")); data.put("Decks",decks);
    }
    public static void setMeteorSpawner(BiConsumer<ServerLevel,Vec3> spawner) { meteorSpawner=spawner; }
    public static boolean hasMeteorSpawner() { return meteorSpawner!=null; }
    static void meteor(ServerLevel level,Vec3 position) { if(meteorSpawner!=null) meteorSpawner.accept(level,position); }
    public static void clear(MinecraftServer server) { RUNTIMES.keySet().removeIf(level->level.getServer()==server); }
    public static void profileBaseline(ServerLevel level,LevelChunk chunk) {
        if(!level.dimension().equals(Level.OVERWORLD) || !level.getServer().isSameThread()) return;
        InstabilityWorldData world=InstabilityWorldData.get(level.getServer());
        if(world.baseline()!=null || world.profile().contains(chunk.getPos().toLong())) return;
        if(world.profile().profile(chunk)) { world.finishBaseline(); world.setDirty(); }
    }
    public static void tickChunk(ServerLevel level,LevelChunk chunk,CompoundTag ageState,Runnable dirty) {
        if(!level.getServer().isSameThread() || !DecayPropagation.isAge(level)) return;
        boolean fresh=!RUNTIMES.containsKey(level);
        InstabilityRuntime runtime=RUNTIMES.computeIfAbsent(level,key->new InstabilityRuntime(level,ageState));
        boolean changed=runtime.profile.profile(chunk);
        if(changed) runtime.data.put("Profile",runtime.profile.save());
        changed|=fresh;
        InstabilityWorldData world=InstabilityWorldData.get(level.getServer());
        boolean enabled=world.enabled() && (!runtime.data.contains("Enabled") || runtime.data.getBoolean("Enabled"));
        Map<String,Integer> baseline=world.baseline();
        if(baseline!=null && runtime.profile.count()>400 && (!runtime.hasBlockScore || baseline.hashCode()!=runtime.baselineHash || runtime.profile.count()>runtime.scoredAt+100)) {
            runtime.blockScore=runtime.profile.score(baseline); runtime.scoredAt=runtime.profile.count(); runtime.hasBlockScore=true; runtime.baselineHash=baseline.hashCode();
        }
        int block=runtime.blockScore;
        int score=InstabilityDecks.score(baseline!=null && runtime.profile.count()>400,
                ageState.getInt("SymbolInstability"),ageState.getInt("BaseInstability"),block,
                runtime.data.getInt("Bonus"),runtime.data.getInt("Debug"),world.difficulty());
        runtime.data.putInt("Score",score); runtime.data.putInt("ProfiledChunks",runtime.profile.count());
        Map<String,Integer> effects=new LinkedHashMap<>();
        if(enabled) effects.putAll(InstabilityDecks.levels(runtime.decks,score));
        CompoundTag explicit=ageState.getCompound("ExplicitEffects");
        for(String key:explicit.getAllKeys()) if(explicit.getInt(key)>0) effects.merge(key.equals("burning")?"burning_explicit":key.equals("lightning")?"lightning_explicit":key,explicit.getInt(key),Integer::sum);
        if(!effects.isEmpty()) { InstabilityEffects.tick(level,chunk,score,effects,runtime.data); changed=true; }
        if(changed) dirty.run();
    }
}
