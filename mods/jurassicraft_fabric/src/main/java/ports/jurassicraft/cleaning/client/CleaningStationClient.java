package ports.jurassicraft.cleaning.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import ports.jurassicraft.cleaning.CleaningStationRegistry;

public final class CleaningStationClient {
    private CleaningStationClient() {}
    public static void init() {
        BlockEntityRenderers.register(CleaningStationRegistry.BLOCK_ENTITY.get(), CleaningStationRenderer::new);
        MenuScreens.register(CleaningStationRegistry.MENU.get(), CleaningStationScreen::new);
    }
}
