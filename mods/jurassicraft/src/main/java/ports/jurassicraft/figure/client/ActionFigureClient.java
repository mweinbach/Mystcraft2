package ports.jurassicraft.figure.client;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.figure.ActionFigureItem;
import ports.jurassicraft.figure.ActionFigureRegistry;
import ports.jurassicraft.figure.ActionFigureCycleRecipe;

@Mod.EventBusSubscriber(modid = PortMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ActionFigureClient {
    private ActionFigureClient() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ActionFigureRegistry.ENTITY.get(), context -> new ActionFigureRenderer());
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ActionFigureRegistry.ITEM.get(), new ResourceLocation(PortMod.MOD_ID, "figure_species"),
                (stack, level, entity, seed) -> ActionFigureCycleRecipe.ORDER.indexOf(ActionFigureItem.species(stack))));
    }
}
