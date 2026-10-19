package ports.jurassicraft.plants.client;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.FoliageColor;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.platform.client.ClientPlatform;
import ports.jurassicraft.plants.PlantRegistry;
import ports.jurassicraft.plants.PlantSpecies;

/** The original leaves and moss take their colors from the surrounding biome. */
public final class PlantColors {
    private PlantColors() {}
    public static void setup() {
        for (PlantSpecies species : PlantSpecies.values()) {
            ClientPlatform.renderLayer(RenderType.cutout(), PlantRegistry.plant(species).get());
            if (!species.isTree()) continue;
            ClientPlatform.renderLayer(RenderType.cutoutMipped(), PlantRegistry.leaves(species).get());
            ClientPlatform.renderLayer(RenderType.cutout(), Registry.BLOCK.get(new ResourceLocation(PortMod.MOD_ID, species.id() + "_door")));
        }
        ClientPlatform.renderLayer(RenderType.cutout(), PlantRegistry.GRACILARIA_SEAWEED.get());
    }
    public static void colors(ClientPlatform.Colors colors) {
        for (PlantSpecies species : PlantSpecies.values()) {
            if (!species.isTree()) continue;
            var leaves = PlantRegistry.leaves(species).get();
            colors.block((state, world, pos, tint) ->
                    world == null || pos == null ? FoliageColor.getDefaultColor() : BiomeColors.getAverageFoliageColor(world, pos), leaves);
            colors.item((stack, tint) -> FoliageColor.getDefaultColor(), leaves);
        }
        colors.block((state, world, pos, tint) -> world == null || pos == null
                ? FoliageColor.getDefaultColor() : BiomeColors.getAverageFoliageColor(world, pos), PlantRegistry.MOSS.get());
    }
}
