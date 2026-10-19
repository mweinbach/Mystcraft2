package ports.jurassicraft.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import ports.jurassicraft.PortMod;

/** Verify resources were not merely packaged, but loaded into the running world. */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DatapackLoadingGameTests {
    private DatapackLoadingGameTests() {}

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void recipesLootAndWorldgenAreLoaded(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var access = level.registryAccess();
        var errors = new ArrayList<String>();
        check(helper, "recipes", id -> server.getRecipeManager().byKey(id).isPresent(), errors);
        check(helper, "loot_tables", id -> server.getLootTables().getIds().contains(id), errors);
        checkRegistry(helper, "worldgen/configured_feature", access.registryOrThrow(Registry.CONFIGURED_FEATURE_REGISTRY), errors);
        checkRegistry(helper, "worldgen/placed_feature", access.registryOrThrow(Registry.PLACED_FEATURE_REGISTRY), errors);
        checkRegistry(helper, "forge/biome_modifier", access.registryOrThrow(ForgeRegistries.Keys.BIOME_MODIFIERS), errors);
        checkRegistry(helper, "worldgen/structure", access.registryOrThrow(Registry.STRUCTURE_REGISTRY), errors);
        checkRegistry(helper, "worldgen/structure_set", access.registryOrThrow(Registry.STRUCTURE_SET_REGISTRY), errors);

        var plains = access.registryOrThrow(Registry.BIOME_REGISTRY).getHolderOrThrow(Biomes.PLAINS).value();
        ResourceLocation amber = new ResourceLocation(PortMod.MOD_ID, "amber_ore");
        boolean applied = plains.getGenerationSettings().features().stream().flatMap(set -> set.stream())
                .anyMatch(holder -> holder.unwrapKey().map(key -> key.location().equals(amber)).orElse(false));
        if (!applied) errors.add("the amber ore biome modifier did not add its feature to plains");
        if (!errors.isEmpty()) helper.fail(String.join("\n", errors));
        helper.succeed();
    }

    private static void checkRegistry(GameTestHelper helper, String path, Registry<?> registry, List<String> errors) {
        check(helper, path, registry::containsKey, errors);
    }

    private static void check(GameTestHelper helper, String folder, Predicate<ResourceLocation> loaded, List<String> errors) {
        var resources = helper.getLevel().getServer().getResourceManager().listResources(folder,
                id -> id.getNamespace().equals(PortMod.MOD_ID) && id.getPath().endsWith(".json"));
        if (resources.isEmpty()) { errors.add("no JurassiCraft resources loaded from " + folder); return; }
        String prefix = folder + "/";
        for (ResourceLocation resource : resources.keySet()) {
            String path = resource.getPath();
            if (!path.startsWith(prefix)) continue;
            ResourceLocation id = new ResourceLocation(PortMod.MOD_ID, path.substring(prefix.length(), path.length() - 5));
            if (!loaded.test(id)) errors.add("not loaded: " + id + " (" + folder + ")");
        }
    }
}
