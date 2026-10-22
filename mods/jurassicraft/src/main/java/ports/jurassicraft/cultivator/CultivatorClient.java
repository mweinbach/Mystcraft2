package ports.jurassicraft.cultivator;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import ports.jurassicraft.platform.client.ClientPlatform;

public final class CultivatorClient {
    private CultivatorClient() {}
    // Upstream's CultivatorRenderer bob is not registered: the bind in RenderingHandler is commented out.
    public static void setup() {
        MenuScreens.register(CultivatorRegistry.MENU.get(), CultivatorScreen::new);
        ClientPlatform.renderLayer(RenderType.translucent(), CultivatorRegistry.CULTIVATOR.get());
    }
}
