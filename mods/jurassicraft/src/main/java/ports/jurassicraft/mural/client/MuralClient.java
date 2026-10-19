package ports.jurassicraft.mural.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.mural.MuralRegistry;

@Mod.EventBusSubscriber(modid = PortMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MuralClient {
    private MuralClient() {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MuralRegistry.ENTITY.get(), MuralRenderer::new);
    }
}
