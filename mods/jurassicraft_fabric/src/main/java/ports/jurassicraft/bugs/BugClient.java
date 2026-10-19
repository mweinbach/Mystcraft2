package ports.jurassicraft.bugs;

import net.minecraft.client.gui.screens.MenuScreens;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;

public final class BugClient {
    private BugClient() {}
    public static void init() {
        MenuScreens.register(BugRegistry.MENU.get(),BugCrateScreen::new);
        BlockRenderLayerMap.INSTANCE.putBlock(BugRegistry.PLANKTON_SWARM.get(),RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BugRegistry.KRILL_SWARM.get(),RenderType.cutout());
    }
}
