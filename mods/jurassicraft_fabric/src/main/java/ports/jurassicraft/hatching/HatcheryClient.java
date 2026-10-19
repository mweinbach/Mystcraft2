package ports.jurassicraft.hatching;

import net.minecraft.client.gui.screens.MenuScreens;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class HatcheryClient {
    private HatcheryClient() {}
    public static void init() {
        BlockEntityRenderers.register(HatcheryRegistry.blockEntity(HatcheryRegistry.Machine.INCUBATOR), IncubatorRenderer::new);
        for (var machine : HatcheryRegistry.Machine.values()) {
            MenuScreens.register(HatcheryRegistry.menu(machine), HatcheryScreen::new);
            BlockRenderLayerMap.INSTANCE.putBlock(HatcheryRegistry.block(machine), RenderType.cutout());
        }
    }
}
