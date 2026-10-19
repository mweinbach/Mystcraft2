package ports.jurassicraft.plants.client;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.FoliageColor;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.plants.PlantRegistry;
import ports.jurassicraft.plants.PlantSpecies;

/** Client initialization for transparent plants and biome-tinted leaves. */
public final class PlantColors {
    private PlantColors() {}
    public static void init() {
        for (PlantSpecies species : PlantSpecies.values()) {
            BlockRenderLayerMap.INSTANCE.putBlock(PlantRegistry.plant(species).get(), RenderType.cutout());
            if (!species.isTree()) continue;
            var leaves = PlantRegistry.leaves(species).get();
            BlockRenderLayerMap.INSTANCE.putBlock(leaves, RenderType.cutoutMipped());
            BlockRenderLayerMap.INSTANCE.putBlock(Registry.BLOCK.get(new ResourceLocation(PortMod.MOD_ID, species.id() + "_door")), RenderType.cutout());
            ColorProviderRegistry.BLOCK.register((state, world, pos, tint) ->
                    world == null || pos == null ? FoliageColor.getDefaultColor() : BiomeColors.getAverageFoliageColor(world, pos), leaves);
            ColorProviderRegistry.ITEM.register((stack, tint) -> FoliageColor.getDefaultColor(), leaves);
        }
        BlockRenderLayerMap.INSTANCE.putBlock(PlantRegistry.GRACILARIA_SEAWEED.get(), RenderType.cutout());
        ColorProviderRegistry.BLOCK.register((state, world, pos, tint) -> world == null || pos == null
                ? FoliageColor.getDefaultColor() : BiomeColors.getAverageFoliageColor(world, pos), PlantRegistry.MOSS.get());
    }
}
