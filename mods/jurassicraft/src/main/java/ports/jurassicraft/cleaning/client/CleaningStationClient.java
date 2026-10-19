package ports.jurassicraft.cleaning.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.cleaning.CleaningStationRegistry;

@Mod.EventBusSubscriber(modid = PortMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CleaningStationClient {
    private CleaningStationClient() {}

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CleaningStationRegistry.BLOCK_ENTITY.get(), CleaningStationRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(CleaningStationRegistry.MENU.get(), CleaningStationScreen::new));
    }
}
