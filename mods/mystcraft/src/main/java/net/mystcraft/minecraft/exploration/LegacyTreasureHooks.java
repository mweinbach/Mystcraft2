package net.mystcraft.minecraft.exploration;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
/** The pinned TreasureGenWrapper.addLoot is empty: its weight dilutes the native main pool. */
public final class LegacyTreasureHooks {
    public static final Set<ResourceLocation> TARGETS=Set.of(new ResourceLocation("minecraft:chests/desert_pyramid"),new ResourceLocation("minecraft:chests/jungle_temple"),new ResourceLocation("minecraft:chests/stronghold_library"),new ResourceLocation("minecraft:chests/simple_dungeon"));
    private LegacyTreasureHooks(){}
    public static Map<ResourceLocation,JsonElement> apply(Map<ResourceLocation,JsonElement> input){
        var result=new LinkedHashMap<ResourceLocation,JsonElement>(input);
        for(var id:TARGETS){
            JsonElement element=input.get(id);if(element==null||!element.isJsonObject())continue;
            JsonObject copy=element.getAsJsonObject().deepCopy();if(!copy.has("pools")||!copy.get("pools").isJsonArray()||copy.getAsJsonArray("pools").size()==0)continue;
            if(!copy.getAsJsonArray("pools").get(0).isJsonObject())continue;
            JsonObject main=copy.getAsJsonArray("pools").get(0).getAsJsonObject();
            for(JsonElement pool:copy.getAsJsonArray("pools"))if(pool.isJsonObject()&&pool.getAsJsonObject().has("name")&&pool.getAsJsonObject().get("name").isJsonPrimitive()&&pool.getAsJsonObject().get("name").getAsString().equals("main")){main=pool.getAsJsonObject();break;}
            if(!main.has("entries")||!main.get("entries").isJsonArray())continue;
            boolean present=false;for(JsonElement entry:main.getAsJsonArray("entries"))if(entry.isJsonObject()&&entry.getAsJsonObject().has("mystcraft_legacy_hook"))present=true;
            if(present)continue;
            JsonObject empty=new JsonObject();empty.addProperty("type","minecraft:empty");empty.addProperty("weight",10);empty.addProperty("mystcraft_legacy_hook",true);main.getAsJsonArray("entries").add(empty);result.put(id,copy);
        }
        return result;
    }
}
