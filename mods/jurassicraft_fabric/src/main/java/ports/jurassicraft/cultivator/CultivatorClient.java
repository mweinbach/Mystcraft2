package ports.jurassicraft.cultivator;

import net.minecraft.client.gui.screens.MenuScreens;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;

public final class CultivatorClient {
    private CultivatorClient() {}
    public static void init() {
        MenuScreens.register(CultivatorRegistry.MENU.get(),CultivatorScreen::new);
        BlockRenderLayerMap.INSTANCE.putBlock(CultivatorRegistry.CULTIVATOR.get(),RenderType.translucent());
    }
}
