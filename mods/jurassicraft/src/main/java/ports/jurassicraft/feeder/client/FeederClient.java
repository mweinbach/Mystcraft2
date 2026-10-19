package ports.jurassicraft.feeder.client;

import net.minecraft.client.gui.screens.MenuScreens;
import ports.jurassicraft.feeder.FeederRegistry;
import ports.jurassicraft.platform.client.ClientPlatform;

public final class FeederClient {
    private FeederClient() {}
    public static void renderers(ClientPlatform.Renderers renderers) {
        renderers.blockEntity(FeederRegistry.BLOCK_ENTITY.get(), FeederRenderer::new);
    }
    public static void setup() { MenuScreens.register(FeederRegistry.MENU.get(), FeederScreen::new); }
}
