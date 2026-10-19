package ports.jurassicraft.plants.client;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.FoliageColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.plants.PlantRegistry;
import ports.jurassicraft.plants.PlantSpecies;

/** The original leaves and moss take their colors from the surrounding biome. */
@Mod.EventBusSubscriber(modid = PortMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PlantColors {
    private PlantColors() {}
    @SubscribeEvent
    public static void blockColors(RegisterColorHandlersEvent.Block event) {
        for (PlantSpecies species : PlantSpecies.values()) {
            if (species.isTree()) event.register((state, world, pos, tint) ->
                    world == null || pos == null ? FoliageColor.getDefaultColor() : BiomeColors.getAverageFoliageColor(world, pos),
                    PlantRegistry.leaves(species).get());
        }
        event.register((state, world, pos, tint) -> world == null || pos == null
                ? FoliageColor.getDefaultColor() : BiomeColors.getAverageFoliageColor(world, pos), PlantRegistry.MOSS.get());
    }
    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        for (PlantSpecies species : PlantSpecies.values()) {
            if (species.isTree()) event.register((stack, tint) -> FoliageColor.getDefaultColor(), PlantRegistry.leaves(species).get());
        }
    }
}
