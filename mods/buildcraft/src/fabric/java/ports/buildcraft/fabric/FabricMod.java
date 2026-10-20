package ports.buildcraft.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.GenerationStep;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.platform.Platform;

/** Fabric entrypoint: installs the platform, registers the shared modules, exposes storages and adds oil wells. */
public final class FabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricPlatform platform = new FabricPlatform();
        Platform.install(platform);
        BuildCraft.init();
        platform.flush();
        BlockEntityType<?>[] types = Registry.BLOCK_ENTITY_TYPE.entrySet().stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals(BuildCraft.MOD_ID))
                .map(entry -> entry.getValue())
                .toArray(BlockEntityType<?>[]::new);
        FabricPorts.register(types);
        FabricShardStorage.register();
        // Forge adds the same placed feature to #minecraft:is_overworld through data/buildcraft/forge/biome_modifier/oil_well.json.
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld().or(BiomeSelectors.tag(BiomeTags.IS_OVERWORLD)),
                GenerationStep.Decoration.LAKES, ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, BuildCraft.id("oil_well")));
    }
}
