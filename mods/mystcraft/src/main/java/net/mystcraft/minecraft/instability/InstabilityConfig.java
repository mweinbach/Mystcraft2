package net.mystcraft.minecraft.instability;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Loader-neutral operator configuration. Values are validated before replacing any persisted settings. */
public record InstabilityConfig(boolean globalEnabled,int difficulty,Map<String,Integer> baseline) {
    public InstabilityConfig {
        if(difficulty<0 || difficulty>3) throw new IllegalArgumentException("difficulty must be an integer from0 to3");
        if(baseline!=null) {
            Map<String,Integer> values=new LinkedHashMap<>();
            for(var entry:baseline.entrySet()) {
                if(ResourceLocation.tryParse(entry.getKey())==null || !InstabilityProfile.FACTORS.containsKey(entry.getKey()))
                    throw new IllegalArgumentException("Unknown watched baseline block: "+entry.getKey());
                if(entry.getValue()==null || entry.getValue()<0) throw new IllegalArgumentException("Baseline values must be nonnegative integers");
                values.put(entry.getKey(),entry.getValue());
            }
            baseline=Map.copyOf(values);
        }
    }
    public static InstabilityConfig parse(JsonObject json) {
        if(json.has("globalEnabled") && (!json.get("globalEnabled").isJsonPrimitive() || !json.getAsJsonPrimitive("globalEnabled").isBoolean()))
            throw new IllegalArgumentException("globalEnabled must be boolean");
        boolean enabled=!json.has("globalEnabled") || json.get("globalEnabled").getAsBoolean();
        int difficulty=json.has("difficulty")?integer(json.get("difficulty"),"difficulty"):2;
        Map<String,Integer> baseline=null;
        if(json.has("baseline") && !json.get("baseline").isJsonNull()) {
            if(!json.get("baseline").isJsonObject())throw new IllegalArgumentException("baseline must be an object or null");
            baseline=new LinkedHashMap<>();
            for(var entry:json.getAsJsonObject("baseline").entrySet())baseline.put(entry.getKey(),integer(entry.getValue(),"baseline."+entry.getKey()));
        }
        for(String key:json.keySet())if(!java.util.Set.of("globalEnabled","difficulty","baseline").contains(key))throw new IllegalArgumentException("Unknown instability setting: "+key);
        return new InstabilityConfig(enabled,difficulty,baseline);
    }
    private static int integer(JsonElement value,String key) {
        if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(key+" must be an integer");
        try{return value.getAsBigDecimal().intValueExact();}catch(ArithmeticException exception){throw new IllegalArgumentException(key+" must be a signed32-bit integer",exception);}
    }
    public static InstabilityConfig load(MinecraftServer server) throws IOException {
        Path path=server.getServerDirectory().toPath().resolve("config/mystcraft-instability.json");
        if(!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            JsonObject defaults=new JsonObject();defaults.addProperty("globalEnabled",true);defaults.addProperty("difficulty",2);
            defaults.add("baseline",com.google.gson.JsonNull.INSTANCE);
            Files.writeString(path,new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(defaults)+"\n",StandardCharsets.UTF_8);
            return new InstabilityConfig(true,2,null);
        }
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            JsonElement root=JsonParser.parseReader(reader);
            if(!root.isJsonObject())throw new IllegalArgumentException("Instability configuration must be a JSON object");
            return parse(root.getAsJsonObject());
        }
    }
}
