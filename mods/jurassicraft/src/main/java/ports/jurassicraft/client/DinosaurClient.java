package ports.jurassicraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.NoopRenderer;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.platform.client.ClientPlatform;

public final class DinosaurClient {
    private DinosaurClient() {}
    public static void renderers(ClientPlatform.Renderers renderers) {
        renderers.entity(DinosaurRegistry.EGG.get(), DinosaurEggRenderer::new);
        renderers.entity(DinosaurRegistry.VENOM.get(), NoopRenderer::new);
        for (Species species : Species.values()) {
            renderers.entity(DinosaurRegistry.type(species), context -> new DinosaurRenderer(context, species));
        }
    }
    public static void setup() { MenuScreens.register(DinosaurRegistry.ORDER_MENU.get(), DinosaurOrderScreen::new); }
    public static void keys() { MicroraptorControls.keys(); }
    public static void tick(Minecraft client) { MicroraptorControls.tick(client); }
}
