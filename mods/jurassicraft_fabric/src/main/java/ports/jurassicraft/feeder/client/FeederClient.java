package ports.jurassicraft.feeder.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import ports.jurassicraft.feeder.FeederRegistry;

public final class FeederClient {
    private FeederClient() {}
    public static void init() {
        BlockEntityRenderers.register(FeederRegistry.BLOCK_ENTITY.get(),FeederRenderer::new);
        MenuScreens.register(FeederRegistry.MENU.get(),FeederScreen::new);
    }
}
