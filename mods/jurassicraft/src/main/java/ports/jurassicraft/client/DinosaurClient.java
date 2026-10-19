package ports.jurassicraft.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurRegistry;

@Mod.EventBusSubscriber(modid = PortMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DinosaurClient {
    private DinosaurClient() {}
    @SubscribeEvent public static void screens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(DinosaurRegistry.ORDER_MENU.get(), DinosaurOrderScreen::new));
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DinosaurRegistry.EGG.get(), DinosaurEggRenderer::new);
        event.registerEntityRenderer(DinosaurRegistry.VENOM.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        for (Species species : Species.values()) {
            event.registerEntityRenderer(DinosaurRegistry.type(species), context -> new DinosaurRenderer(context, species));
        }
    }
}
