package ports.jurassicraft.machine;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class DnaMachineClient {
    private DnaMachineClient() {}
    public static void init() {
        MenuScreens.register(DnaMachineRegistry.MENU.get(), DnaMachineScreen::new);
        BlockEntityRenderers.register(DnaMachineRegistry.ENTITY.get(), DnaMachineRenderer::new);
    }
}
