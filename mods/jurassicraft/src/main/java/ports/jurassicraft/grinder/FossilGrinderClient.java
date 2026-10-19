package ports.jurassicraft.grinder;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import ports.jurassicraft.platform.client.ClientPlatform;

public final class FossilGrinderClient {
    private FossilGrinderClient() {}
    public static void setup() {
        MenuScreens.register(FossilGrinderRegistry.FOSSIL_GRINDER_MENU.get(), FossilGrinderScreen::new);
        ClientPlatform.renderLayer(RenderType.cutout(), FossilGrinderRegistry.FOSSIL_GRINDER.get());
    }
}
