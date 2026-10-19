package ports.jurassicraft.machine;

import net.minecraft.client.gui.screens.MenuScreens;
import ports.jurassicraft.platform.client.ClientPlatform;

public final class DnaMachineClient {
    private DnaMachineClient() {}
    public static void setup() { MenuScreens.register(DnaMachineRegistry.MENU.get(), DnaMachineScreen::new); }
    public static void renderers(ClientPlatform.Renderers renderers) {
        renderers.blockEntity(DnaMachineRegistry.ENTITY.get(), DnaMachineRenderer::new);
    }
}
