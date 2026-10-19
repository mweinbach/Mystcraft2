package net.mystcraft.minecraft.linking;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
/** Stable identities for non-Age worlds; Age identities come from canonical AgeSavedData UUIDs. */
public final class DimensionIdentityData extends SavedData {
    private final Map<ResourceLocation,UUID> identities=new HashMap<>();private final Set<ResourceLocation> dead=new HashSet<>();
    public DimensionIdentityData(){}
    public static DimensionIdentityData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(DimensionIdentityData::load,DimensionIdentityData::new,"mystcraft_dimension_identities");}
    public UUID identity(ResourceLocation dimension){return identities.computeIfAbsent(dimension,key->{setDirty();return UUID.nameUUIDFromBytes(("mystcraft:"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8));});}
    public boolean dead(ResourceLocation dimension){return dead.contains(dimension);}
    public void dead(ResourceLocation dimension,boolean dead){if(dead)this.dead.add(dimension);else this.dead.remove(dimension);setDirty();}
    @Override public CompoundTag save(CompoundTag tag){CompoundTag ids=new CompoundTag();identities.forEach((key,id)->ids.putUUID(key.toString(),id));tag.put("Identities",ids);ListTag dead=new ListTag();this.dead.stream().sorted().forEach(key->dead.add(StringTag.valueOf(key.toString())));tag.put("Dead",dead);return tag;}
    public static DimensionIdentityData load(CompoundTag tag){DimensionIdentityData data=new DimensionIdentityData();CompoundTag ids=tag.getCompound("Identities");for(String key:ids.getAllKeys()){ResourceLocation dimension=ResourceLocation.tryParse(key);if(dimension!=null&&ids.hasUUID(key))data.identities.put(dimension,ids.getUUID(key));}for(var value:tag.getList("Dead",8)){ResourceLocation dimension=ResourceLocation.tryParse(value.getAsString());if(dimension!=null)data.dead.add(dimension);}return data;}
}
