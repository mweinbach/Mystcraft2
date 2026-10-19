package ports.jurassicraft.skull.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.skull.SkullRegistry;

@Mod.EventBusSubscriber(modid=PortMod.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class SkullClient {
    private SkullClient() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerBlockEntityRenderer(SkullRegistry.ENTITY.get(),context -> new SkullRenderer()); }
}
