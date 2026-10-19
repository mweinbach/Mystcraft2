package net.mystcraft.smoke;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTables;
import net.mystcraft.minecraft.exploration.ClassicLibraryFeature;

/** Tests the actual loaded authored pool separately from proven appended third-party loot pools. */
public final class LoadedTreasureFixture {
    public record Source(LootTable table,LootTable whole,Set<String> foreignItems,int foreignPools,JsonObject loaded){}
    private LoadedTreasureFixture(){}
    private static java.util.function.BiFunction<MinecraftServer,JsonObject,LootTable> decoder=(server,json)->Deserializers.createLootTableSerializer().create().fromJson(json,LootTable.class);
    public static void installDecoder(java.util.function.BiFunction<MinecraftServer,JsonObject,LootTable> platformDecoder){decoder=java.util.Objects.requireNonNull(platformDecoder);}
    public static Source load(MinecraftServer server){
        LootTable whole=server.getLootTables().get(ClassicLibraryFeature.LOOT);
        JsonObject loaded=LootTables.serialize(whole).getAsJsonObject();JsonArray pools=loaded.getAsJsonArray("pools");
        require(pools!=null&&pools.size()>0,"loaded treasure has no pools: "+loaded);
        JsonObject source=null;int sourceIndex=-1,matches=0;Set<String> foreignItems=new LinkedHashSet<>();
        for(int i=0;i<pools.size();i++){
            JsonObject pool=pools.get(i).getAsJsonObject();boolean authored=false;
            for(JsonElement entry:pool.getAsJsonArray("entries"))if(entry.isJsonObject()&&entry.getAsJsonObject().has("functions"))for(JsonElement function:entry.getAsJsonObject().getAsJsonArray("functions"))if(function.getAsJsonObject().get("function").getAsString().equals("mystcraft:treasure"))authored=true;
            if(authored){source=pool;sourceIndex=i;matches++;}else collectItems(pool,foreignItems);
        }
        require(matches==1&&sourceIndex==0,"expected exactly one authored first pool before appended pack pools: "+loaded);
        uniform(source.getAsJsonObject("rolls"),4,8,"rolls");uniform(source.getAsJsonObject("bonus_rolls"),1,2,"bonus_rolls");
        require(!source.has("conditions")||source.getAsJsonArray("conditions").size()==0,"source pool gained conditions: "+source);
        require(!source.has("functions")||source.getAsJsonArray("functions").size()==0,"source pool gained functions: "+source);
        var entries=source.getAsJsonArray("entries");require(entries.size()==1,"source must retain sole placeholder entry: "+source);
        JsonObject entry=entries.get(0).getAsJsonObject();require(entry.get("type").getAsString().equals("minecraft:item")&&entry.get("name").getAsString().equals("mystcraft:page"),"source placeholder changed: "+source);
        require(!entry.has("weight")||entry.get("weight").getAsInt()==1,"source placeholder weight changed");require(!entry.has("quality")||entry.get("quality").getAsInt()==0,"source placeholder quality changed");
        require(!entry.has("conditions")||entry.getAsJsonArray("conditions").size()==0,"source placeholder gained conditions");
        var functions=entry.getAsJsonArray("functions");require(functions!=null&&functions.size()==1&&functions.get(0).getAsJsonObject().get("function").getAsString().equals("mystcraft:treasure"),"source treasure function changed: "+source);
        JsonObject isolated=new JsonObject();isolated.addProperty("type","minecraft:chest");JsonArray onePool=new JsonArray();onePool.add(source.deepCopy());isolated.add("pools",onePool);
        LootTable nativeSource=decoder.apply(server,isolated);
        return new Source(nativeSource,whole,Set.copyOf(foreignItems),pools.size()-1,loaded);
    }
    private static void uniform(JsonObject value,int min,int max,String name){require(value!=null&&value.get("type").getAsString().equals("minecraft:uniform")&&value.get("min").getAsDouble()==min&&value.get("max").getAsDouble()==max,"source "+name+" must retain uniform "+min+".."+max+": "+value);}
    private static void collectItems(JsonElement element,Set<String> output){
        if(element.isJsonArray())for(JsonElement child:element.getAsJsonArray())collectItems(child,output);
        else if(element.isJsonObject()){var object=element.getAsJsonObject();if(object.has("type")&&object.get("type").isJsonPrimitive()&&object.get("type").getAsString().equals("minecraft:item")&&object.has("name"))output.add(object.get("name").getAsString());for(var entry:object.entrySet())if(entry.getValue().isJsonObject()||entry.getValue().isJsonArray())collectItems(entry.getValue(),output);}
    }
    /** Identical seed runs the first authored pool before any added pool can consume random values. */
    public static int verifyWhole(Source source,List<ItemStack> expected,List<ItemStack> actual,long seed){
        var remaining=new ArrayList<ItemStack>(actual);
        for(ItemStack stack:expected){int found=-1;for(int i=0;i<remaining.size();i++)if(ItemStack.matches(stack,remaining.get(i))){found=i;break;}if(found<0)throw new AssertionError("whole loaded table changed authored NBT/count at seed="+seed+" expected="+describe(expected)+" actual="+describe(actual)+" table="+source.loaded());remaining.remove(found);}
        for(ItemStack extra:remaining)if(!source.foreignItems().contains(Registry.ITEM.getKey(extra.getItem()).toString()))throw new AssertionError("unexplained loot not declared by appended pack pools at seed="+seed+": "+describe(actual)+" table="+source.loaded());
        return remaining.size();
    }
    public static List<String> describe(List<ItemStack> stacks){return stacks.stream().map(stack->stack.save(new CompoundTag()).toString()).toList();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
