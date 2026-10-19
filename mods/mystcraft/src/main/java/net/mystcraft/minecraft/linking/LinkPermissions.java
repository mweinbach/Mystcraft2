package net.mystcraft.minecraft.linking;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Classic username-keyed permission semantics; restrictions win over optional permit whitelists. */
public final class LinkPermissions extends SavedData {
    public enum Direction { ENTRY, DEPART }
    private final Map<String,Set<ResourceLocation>> permitEntry=new HashMap<>(),restrictEntry=new HashMap<>(),permitDepart=new HashMap<>(),restrictDepart=new HashMap<>();
    public LinkPermissions(){}
    public static LinkPermissions get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(LinkPermissions::load,LinkPermissions::new,"mystcraft-linking-permissions");}
    public boolean allows(String name,ResourceLocation origin,ResourceLocation destination){return allows(name,Direction.DEPART,origin)&&allows(name,Direction.ENTRY,destination);}
    public boolean allows(String name,Direction direction,ResourceLocation dimension){
        Set<ResourceLocation> restricted=restricted(direction).get(name),permitted=permitted(direction).get(name);
        return (restricted==null || !restricted.contains(dimension)) && (permitted==null || permitted.contains(dimension));
    }
    public void change(String name,boolean permit,Direction direction,ResourceLocation dimension){
        if(name==null || name.isBlank() || name.length()>64)throw new IllegalArgumentException("Invalid player name");
        Map<String,Set<ResourceLocation>> permitted=permitted(direction),restricted=restricted(direction);
        if(dimension==null){restricted.remove(name);if(permit)permitted.remove(name);else permitted.put(name,new HashSet<>());}
        else if(permit){if(permitted.containsKey(name))permitted.get(name).add(dimension);if(restricted.containsKey(name))restricted.get(name).remove(dimension);}
        else{if(permitted.containsKey(name))permitted.get(name).remove(dimension);restricted.computeIfAbsent(name,key->new HashSet<>()).add(dimension);}
        setDirty();
    }
    private Map<String,Set<ResourceLocation>> permitted(Direction direction){return direction==Direction.ENTRY?permitEntry:permitDepart;}
    private Map<String,Set<ResourceLocation>> restricted(Direction direction){return direction==Direction.ENTRY?restrictEntry:restrictDepart;}
    @Override public CompoundTag save(CompoundTag tag){tag.put("PermitEntry",write(permitEntry));tag.put("RestrictEntry",write(restrictEntry));tag.put("PermitDepart",write(permitDepart));tag.put("RestrictDepart",write(restrictDepart));return tag;}
    public static LinkPermissions load(CompoundTag tag){LinkPermissions result=new LinkPermissions();read(tag.getCompound("PermitEntry"),result.permitEntry);read(tag.getCompound("RestrictEntry"),result.restrictEntry);read(tag.getCompound("PermitDepart"),result.permitDepart);read(tag.getCompound("RestrictDepart"),result.restrictDepart);return result;}
    private static CompoundTag write(Map<String,Set<ResourceLocation>> map){CompoundTag tag=new CompoundTag();map.forEach((name,dimensions)->{ListTag list=new ListTag();dimensions.stream().sorted().forEach(dimension->list.add(StringTag.valueOf(dimension.toString())));tag.put(name,list);});return tag;}
    private static void read(CompoundTag tag,Map<String,Set<ResourceLocation>> map){for(String name:tag.getAllKeys()){Set<ResourceLocation> dimensions=new HashSet<>();for(var value:tag.getList(name,8)){ResourceLocation dimension=ResourceLocation.tryParse(value.getAsString());if(dimension!=null)dimensions.add(dimension);}map.put(name,dimensions);}}
}
