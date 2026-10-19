package ports.jurassicraft.hatching;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ports.jurassicraft.PortMod;

@Mod.EventBusSubscriber(modid=PortMod.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class HatcheryClient {
    private HatcheryClient() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(HatcheryRegistry.blockEntity(HatcheryRegistry.Machine.INCUBATOR), IncubatorRenderer::new);
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) { event.enqueueWork(() -> {
        for (var machine : HatcheryRegistry.Machine.values()) {
            MenuScreens.register(HatcheryRegistry.menu(machine),HatcheryScreen::new);
            ItemBlockRenderTypes.setRenderLayer(HatcheryRegistry.block(machine),RenderType.cutout());
        }
    }); }
}
