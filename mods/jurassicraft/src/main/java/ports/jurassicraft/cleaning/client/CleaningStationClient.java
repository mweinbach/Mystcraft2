package ports.jurassicraft.cleaning.client;

import net.minecraft.client.gui.screens.MenuScreens;
import ports.jurassicraft.cleaning.CleaningStationBlockEntity;
import ports.jurassicraft.cleaning.CleaningStationRegistry;
import ports.jurassicraft.platform.client.ClientPlatform;

public final class CleaningStationClient {
    private CleaningStationClient() {}
    public static void renderers(ClientPlatform.Renderers renderers) {
        renderers.blockEntity(CleaningStationRegistry.BLOCK_ENTITY.get(), CleaningStationRenderer::new);
    }
    public static void setup() {
        MenuScreens.register(CleaningStationRegistry.MENU.get(), CleaningStationScreen::new);
        CleaningStationBlockEntity.clientWash = WashingParticles::spawn;
    }
}
