package ports.jurassicraft.grinder;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;

public final class FossilGrinderClient {
    private FossilGrinderClient() {}
    public static void init() {
        MenuScreens.register(FossilGrinderRegistry.FOSSIL_GRINDER_MENU.get(), FossilGrinderScreen::new);
        BlockRenderLayerMap.INSTANCE.putBlock(FossilGrinderRegistry.FOSSIL_GRINDER.get(), RenderType.cutout());
    }
}
