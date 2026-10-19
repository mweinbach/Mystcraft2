package ports.jurassicraft.bugs;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ports.jurassicraft.PortMod;

@Mod.EventBusSubscriber(modid=PortMod.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BugClient {
    private BugClient() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) { event.enqueueWork(() -> {
        MenuScreens.register(BugRegistry.MENU.get(),BugCrateScreen::new);
        ItemBlockRenderTypes.setRenderLayer(BugRegistry.PLANKTON_SWARM.get(),RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(BugRegistry.KRILL_SWARM.get(),RenderType.cutout());
    }); }
}
