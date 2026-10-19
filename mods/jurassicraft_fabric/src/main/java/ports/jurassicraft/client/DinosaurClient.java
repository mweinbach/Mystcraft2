package ports.jurassicraft.client;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;
import net.minecraft.client.renderer.entity.NoopRenderer;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurRegistry;

/** Registered only from the Fabric client initializer. */
public final class DinosaurClient {
    private DinosaurClient() {}
    public static void init() {
        ScreenRegistry.register(DinosaurRegistry.ORDER_MENU.get(), DinosaurOrderScreen::new);
        EntityRendererRegistry.register(DinosaurRegistry.EGG.get(), DinosaurEggRenderer::new);
        EntityRendererRegistry.register(DinosaurRegistry.VENOM.get(), NoopRenderer::new);
        for (Species species : Species.values()) {
            EntityRendererRegistry.register(DinosaurRegistry.type(species), context -> new DinosaurRenderer(context, species));
        }
        MicroraptorControls.init();
    }
}
