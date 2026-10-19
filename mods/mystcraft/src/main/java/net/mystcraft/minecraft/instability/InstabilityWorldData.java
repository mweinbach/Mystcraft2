package net.mystcraft.minecraft.instability;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-owned baseline and operator settings, independent of any Age and persisted on restart. */
public final class InstabilityWorldData extends SavedData {
    private InstabilityProfile profile=new InstabilityProfile();
    private Map<String,Integer> baseline;
    private boolean enabled=true;
    private int difficulty=2;
    private boolean configLoaded;
    private boolean explicitBaseline;
    public InstabilityWorldData() {}
    public static InstabilityWorldData get(MinecraftServer server) {
        InstabilityWorldData data=server.overworld().getDataStorage().computeIfAbsent(InstabilityWorldData::load,InstabilityWorldData::new,"mystcraft_instability");
        if(!data.configLoaded) {
            data.configLoaded=true;
            try {
                InstabilityConfig config=InstabilityConfig.load(server);
                data.configure(config.globalEnabled(),config.difficulty(),config.baseline());
            } catch(java.io.IOException | IllegalArgumentException | com.google.gson.JsonParseException error) {
                // Invalid operator config cannot activate destructive providers. Keep the last
                // valid baseline on disk for repair, but stop automatic effects for this session.
                data.enabled=false;
                com.mojang.logging.LogUtils.getLogger().error("Invalid config/mystcraft-instability.json; automatic classic instability disabled until restart: {}",error.toString());
            }
        }
        return data;
    }
    public InstabilityProfile profile() { return profile; }
    public Map<String,Integer> baseline() { return baseline==null ? null : Map.copyOf(baseline); }
    public boolean enabled() { return enabled; }
    public int difficulty() { return difficulty; }
    public void configure(boolean enabled,int difficulty,Map<String,Integer> configuredBaseline) {
        if(difficulty<0 || difficulty>3) throw new IllegalArgumentException("Classic difficulty must be0..3");
        this.enabled=enabled; this.difficulty=difficulty;
        if(configuredBaseline!=null) { baseline=new LinkedHashMap<>(configuredBaseline); explicitBaseline=true; }
        setDirty();
    }
    void finishBaseline() {
        if(baseline!=null || profile.count()<=400) return;
        baseline=new LinkedHashMap<>(); profile.split().forEach((key,value)->baseline.put(key,value.intValue())); setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("ExplicitBaseline",explicitBaseline); tag.putBoolean("Enabled",enabled); tag.putInt("Difficulty",difficulty); tag.put("Profile",profile.save());
        if(baseline!=null) { CompoundTag values=new CompoundTag(); baseline.forEach(values::putInt); tag.put("Baseline",values); } else tag.remove("Baseline");
        return tag;
    }
    public static InstabilityWorldData load(CompoundTag tag) {
        InstabilityWorldData result=new InstabilityWorldData(); result.enabled=!tag.contains("Enabled") || tag.getBoolean("Enabled");
        result.difficulty=tag.contains("Difficulty") ? Math.max(0,Math.min(3,tag.getInt("Difficulty"))) : 2;
        boolean compatible=InstabilityProfile.compatible(tag.getCompound("Profile"));
        result.profile=InstabilityProfile.load(tag.getCompound("Profile"));
        result.explicitBaseline=tag.getBoolean("ExplicitBaseline");
        if(tag.contains("Baseline",10) && (compatible || result.explicitBaseline)) { result.baseline=new LinkedHashMap<>(); CompoundTag baseline=tag.getCompound("Baseline");
            for(String key:baseline.getAllKeys()) result.baseline.put(key,baseline.getInt(key)); }
        if(!compatible) result.setDirty();
        return result;
    }
}
