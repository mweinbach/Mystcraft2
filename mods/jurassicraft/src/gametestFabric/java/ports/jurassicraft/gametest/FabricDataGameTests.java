package ports.jurassicraft.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import ports.jurassicraft.PortMod;

/** Check the resources actually loaded by the server, not just JSON syntax. */
public final class FabricDataGameTests implements FabricGameTest {
    public FabricDataGameTests() {}
    private static void check(GameTestHelper h,boolean ok,String message) { if (!ok) h.fail(message); }
    private static ResourceLocation contentId(ResourceLocation file,String directory) {
        String path=file.getPath();
        return new ResourceLocation(file.getNamespace(),path.substring(directory.length()+1,path.length()-5));
    }
    private static boolean ours(ResourceLocation file) { return file.getNamespace().equals(PortMod.MOD_ID) && file.getPath().endsWith(".json"); }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void recipesLootAndWorldgenActuallyLoad(GameTestHelper h) {
        var server=h.getLevel().getServer(); ResourceManager resources=server.getResourceManager();
        var recipes=resources.listResources("recipes",FabricDataGameTests::ours);
        check(h,!recipes.isEmpty(),"no mod recipes found in the active data pack");
        recipes.forEach((file,resource) -> {
            var id=contentId(file,"recipes");
            check(h,server.getRecipeManager().byKey(id).isPresent(),"recipe failed to load: "+id);
        });
        var loot=resources.listResources("loot_tables",FabricDataGameTests::ours);
        check(h,!loot.isEmpty(),"no mod loot tables found in the active data pack");
        loot.forEach((file,resource) -> {
            var id=contentId(file,"loot_tables");
            check(h,server.getLootTables().getIds().contains(id),"loot table failed to load: "+id);
        });
        var access=h.getLevel().registryAccess();
        var configured=access.registryOrThrow(Registry.CONFIGURED_FEATURE_REGISTRY);
        var placed=access.registryOrThrow(Registry.PLACED_FEATURE_REGISTRY);
        resources.listResources("worldgen/configured_feature",FabricDataGameTests::ours).forEach((file,resource) -> {
            var id=contentId(file,"worldgen/configured_feature");
            check(h,configured.containsKey(id),"configured feature failed to load: "+id);
        });
        resources.listResources("worldgen/placed_feature",FabricDataGameTests::ours).forEach((file,resource) -> {
            var id=contentId(file,"worldgen/placed_feature");
            check(h,placed.containsKey(id),"placed feature failed to load: "+id);
        });
        h.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void requiredModTagValuesAreRegistered(GameTestHelper h) {
        validateTags(h,"tags/items",Registry.ITEM);
        validateTags(h,"tags/blocks",Registry.BLOCK);
        h.succeed();
    }
    private static <T> void validateTags(GameTestHelper h,String directory,Registry<T> registry) {
        var resources=h.getLevel().getServer().getResourceManager().listResources(directory,FabricDataGameTests::ours);
        resources.forEach((file,resource) -> {
            try (var reader=new InputStreamReader(resource.open(),StandardCharsets.UTF_8)) {
                JsonObject json=JsonParser.parseReader(reader).getAsJsonObject();
                for (JsonElement value : json.getAsJsonArray("values")) {
                    String text;
                    boolean required=true;
                    if (value.isJsonPrimitive()) text=value.getAsString();
                    else {
                        JsonObject object=value.getAsJsonObject(); text=object.get("id").getAsString();
                        if (object.has("required")) required=object.get("required").getAsBoolean();
                    }
                    if (!required) continue;
                    boolean nested=text.startsWith("#");
                    ResourceLocation id=ResourceLocation.tryParse(nested ? text.substring(1) : text);
                    check(h,id!=null,"invalid tag reference "+text+" in "+file);
                    if (!id.getNamespace().equals(PortMod.MOD_ID)) continue;
                    boolean present=nested ? registry.getTag(TagKey.create(registry.key(),id)).isPresent() : registry.containsKey(id);
                    check(h,present,"unregistered tag value "+text+" in "+file);
                }
                var tag=TagKey.create(registry.key(),contentId(file,directory));
                check(h,registry.getTag(tag).isPresent(),"tag did not load: "+tag.location());
            } catch (Exception exception) { h.fail("cannot inspect tag "+file+": "+exception); }
        });
    }
}
