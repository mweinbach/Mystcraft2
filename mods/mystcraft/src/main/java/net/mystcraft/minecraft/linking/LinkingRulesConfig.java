package net.mystcraft.minecraft.linking;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Source connection/respawn defaults, with modern registry keys replacing integer dimension IDs. */
public record LinkingRulesConfig(boolean respawnInAges,boolean requireUUID,ResourceLocation homeDimension){
    private static final java.util.Map<MinecraftServer,LinkingRulesConfig> CONFIGS=new WeakHashMap<>();
    public static final LinkingRulesConfig DEFAULT=new LinkingRulesConfig(true,false,new ResourceLocation("minecraft:overworld"));
    public LinkingRulesConfig{java.util.Objects.requireNonNull(homeDimension);}
    public static LinkingRulesConfig parse(JsonObject json){
        for(String key:json.keySet())if(!java.util.Set.of("respawnInAges","requireUUID","homeDimension").contains(key))throw new IllegalArgumentException("Unknown linking rule: "+key);
        for(String key:java.util.List.of("respawnInAges","requireUUID"))if(json.has(key)&&(!json.get(key).isJsonPrimitive()||!json.getAsJsonPrimitive(key).isBoolean()))throw new IllegalArgumentException(key+" must be boolean");
        ResourceLocation home=DEFAULT.homeDimension;
        if(json.has("homeDimension")){
            if(!json.get("homeDimension").isJsonPrimitive()||!json.getAsJsonPrimitive("homeDimension").isString())throw new IllegalArgumentException("homeDimension must be a dimension registry key");
            home=ResourceLocation.tryParse(json.get("homeDimension").getAsString());if(home==null)throw new IllegalArgumentException("Invalid home dimension");
        }
        return new LinkingRulesConfig(!json.has("respawnInAges")||json.get("respawnInAges").getAsBoolean(),json.has("requireUUID")&&json.get("requireUUID").getAsBoolean(),home);
    }
    public static LinkingRulesConfig get(MinecraftServer server){
        return CONFIGS.computeIfAbsent(server,key->{
            var path=server.getServerDirectory().toPath().resolve("config/mystcraft-linking.json");
            try{
                if(!Files.exists(path)){
                    Files.createDirectories(path.getParent());JsonObject defaults=new JsonObject();defaults.addProperty("respawnInAges",true);defaults.addProperty("requireUUID",false);defaults.addProperty("homeDimension",DEFAULT.homeDimension.toString());
                    Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(defaults)+"\n",StandardCharsets.UTF_8);return DEFAULT;
                }
                try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){return parse(JsonParser.parseReader(reader).getAsJsonObject());}
            }catch(java.io.IOException|RuntimeException error){throw new IllegalStateException("Invalid config/mystcraft-linking.json: "+error.getMessage(),error);}
        });
    }
    public static void clear(MinecraftServer server){CONFIGS.remove(server);}
}
