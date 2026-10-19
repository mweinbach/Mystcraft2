package ports.jurassicraft.bugs;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import ports.jurassicraft.platform.client.ClientPlatform;

public final class BugClient {
    private BugClient() {}
    public static void setup() {
        MenuScreens.register(BugRegistry.MENU.get(),BugCrateScreen::new);
        ClientPlatform.renderLayer(RenderType.cutout(), BugRegistry.PLANKTON_SWARM.get(), BugRegistry.KRILL_SWARM.get());
    }
}
