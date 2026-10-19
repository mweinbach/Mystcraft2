package ports.jurassicraft.worldgen;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.ForgeConfigSpec;
import ports.jurassicraft.config.JurassicConfig;
import ports.jurassicraft.content.Species;

/** Runs against Forge classes without a world or a Minecraft server. */
public final class WorldgenConfigCheck {
    private static ResourceKey<? extends net.minecraft.core.Registry<PlacedFeature>> registry;
    private static Holder<PlacedFeature> feature(String id) {
        return Holder.Reference.createStandAlone(null, ResourceKey.create(registry, new ResourceLocation(id)));
    }
    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        // The mapped game jar is not transformed by ModLauncher in this focused check.
        // Permit ResourceKey's registry constants without starting Forge networking.
        net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrapped = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        registry = ResourceKey.createRegistryKey(new ResourceLocation("minecraft", "worldgen/placed_feature"));
        var field = JurassicConfig.class.getDeclaredField("SPEC");
        field.setAccessible(true);
        ForgeConfigSpec spec = (ForgeConfigSpec) field.get(null);
        CommentedConfig config = CommentedConfig.inMemory();
        spec.setConfig(config);
        require(!JurassicConfig.NATURAL_GOAT_SPAWNING.get(), "goat spawning default must be false");
        require(!JurassicConfig.NATURAL_DINOSAUR_SPAWNING.get(), "dinosaur spawning default must be false");
        require(JurassicConfig.PEAT_SPREAD_DISTANCE.get() == 20, "peat spread distance must default to 20");
        config.set("plantGeneration.peatGenerationSpeed", 0);
        spec.afterReload();
        require(JurassicConfig.PEAT_SPREAD_DISTANCE.get() == 0, "peat spreading should be configurable");
        config.set("plantGeneration.peatGenerationSpeed", 20);
        spec.afterReload();

        String[] fixed = {"fossil_nest", "fossil_trackway", "coal_plant_fossils", "amber_ore", "ice_shard",
                "gypsum_stone", "petrified_tree", "moss", "peat", "west_indian_lilac", "gracilaria"};
        WorldgenCategory[] categories = {WorldgenCategory.NEST_FOSSILS, WorldgenCategory.TRACKWAYS,
                WorldgenCategory.PLANT_FOSSILS, WorldgenCategory.AMBER, WorldgenCategory.ICE_SHARDS,
                WorldgenCategory.GYPSUM, WorldgenCategory.PETRIFIED_TREES, WorldgenCategory.MOSS,
                WorldgenCategory.PEAT, WorldgenCategory.FLOWERS, WorldgenCategory.GRACILARIA};
        List<Holder<PlacedFeature>> original = new ArrayList<>();
        for (int i = 0; i < fixed.length; i++) {
            require(WorldgenCategory.forFeature(new ResourceLocation("jurassicraft", fixed[i])) == categories[i],
                    "wrong category for " + fixed[i]);
            original.add(feature("jurassicraft:" + fixed[i]));
        }
        for (Species species : Species.values()) {
            String name = species.id() + "_fossil_block";
            require(WorldgenCategory.forFeature(new ResourceLocation("jurassicraft", name)) == WorldgenCategory.FOSSILS,
                    "wrong category for " + name);
            original.add(feature("jurassicraft:" + name));
        }
        original.add(feature("minecraft:ore_coal_upper"));
        original.add(feature("othermod:peat"));
        for (WorldgenCategory category : WorldgenCategory.values()) {
            require(JurassicConfig.worldgenEnabled(category), category + " must default to enabled");
            require(original.stream().anyMatch(h -> h.unwrapKey().map(k -> WorldgenCategory.forFeature(k.location()) == category).orElse(false)),
                    "no feature mapped for " + category);
        }
        List<Holder<PlacedFeature>> enabled = new ArrayList<>(original);
        WorldgenBiomeModifier.removeDisabled(enabled, JurassicConfig::worldgenEnabled);
        require(enabled.equals(original), "enabled configuration changed the feature ordering");
        for (WorldgenCategory category : WorldgenCategory.values()) {
            config.set(category.section() + "." + category.key(), false);
            spec.afterReload();
            List<Holder<PlacedFeature>> actual = new ArrayList<>(original);
            WorldgenBiomeModifier.removeDisabled(actual, JurassicConfig::worldgenEnabled);
            List<Holder<PlacedFeature>> expected = original.stream()
                    .filter(h -> h.unwrapKey().map(k -> WorldgenCategory.forFeature(k.location()) != category).orElse(true)).toList();
            require(actual.equals(expected), "wrong features removed for " + category);
            require(actual.contains(original.get(original.size() - 2)), "vanilla coal was removed");
            require(actual.contains(original.get(original.size() - 1)), "other mod was removed");
            config.set(category.section() + "." + category.key(), true);
            spec.afterReload();
        }
        System.out.println("Worldgen config defaults and category isolation passed");
    }
}
