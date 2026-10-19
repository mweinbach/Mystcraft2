package ports.jurassicraft.grinder;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ports.jurassicraft.PortMod;

@Mod.EventBusSubscriber(modid = PortMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FossilGrinderClient {
    private FossilGrinderClient() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(FossilGrinderRegistry.FOSSIL_GRINDER_MENU.get(), FossilGrinderScreen::new);
            ItemBlockRenderTypes.setRenderLayer(FossilGrinderRegistry.FOSSIL_GRINDER.get(), RenderType.cutout());
        });
    }
}
